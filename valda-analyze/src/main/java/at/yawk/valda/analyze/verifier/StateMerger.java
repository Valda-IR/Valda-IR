package at.yawk.valda.analyze.verifier;

import at.yawk.valda.ir.Types;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.LocalVariable;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@UtilityClass
final class StateMerger {
    // merge is the same as the ∩ operator in comments in this class.

    /*
     * There is one very, very important invariant for this class: states must *always* converge when merged. That
     * means that there must be no x, a_i so that:
     *
     * (((x ∩ a_0) ∩ a_1) ... a_i) = x but
     * (((x ∩ a_0) ∩ a_1) ... a_i-1) ≠ x
     */

    public static State merge(@Nullable LocalVariable variable, State lhs, State rhs) {
        return mergeWithOp(variable, lhs, rhs, null);
    }

    private static State.Narrow mergeWithOp(
            State.Narrow lhs,
            State.Narrow rhs,
            @Nullable BinaryOperation.Type operation
    ) {
        int lmin = State.Narrow.roundDown(lhs.getMin());
        int lmax = State.Narrow.roundUp(lhs.getMax());
        int rmin = State.Narrow.roundDown(rhs.getMin());
        int rmax = State.Narrow.roundUp(rhs.getMax());

        if (operation == null) {
            if (lhs.contains(rhs)) {
                return lhs;
            } else if (rhs.contains(lhs)) {
                return rhs;
            } else {
                State.Narrow next = new State.Narrow(Math.min(lmin, rmin), Math.max(lmax, rmax),
                                                     lhs.isCanBeFloat() && rhs.isCanBeFloat());
                next.markParent(lhs);
                next.markParent(rhs);
                return next;
            }
        } else if (operation == BinaryOperation.Type.OR_INT || operation == BinaryOperation.Type.XOR_INT) {
            if (lmin < 0 || rmin < 0) {
                return State.Narrow.INT;
            } else {
                return new State.Narrow(0, Math.max(lmax, rmax), false);
            }
        } else if (operation == BinaryOperation.Type.AND_INT) {
            if (lmin < 0 && rmin < 0) {
                return State.Narrow.INT;
            } else {
                int lmask = lmin < 0 ? -1 : lmax;
                int rmask = rmin < 0 ? -1 : rmax;
                return new State.Narrow(0, lmask & rmask, false);
            }
        } else {
            return State.Narrow.INT;
        }
    }

    /**
     * @param operation An optional merge operation. For narrow values, this can be used to "predict" the bounds of
     *                  the output.
     */
    static State mergeWithOp(
            @Nullable LocalVariable variable, State lhs, State rhs,
            @Nullable BinaryOperation.Type operation
    ) {
        if (lhs.equals(rhs) && operation == null) {
            return lhs;
        }

        {
            Kind lhsKind = Kind.forState(lhs);
            Kind rhsKind = Kind.forState(rhs);
            if (lhsKind.type != null && rhsKind.type != null && lhsKind.type != rhsKind.type) {
                throw new AssertionError(
                        "Trying to merge fundamentally incompatible states on " + variable + ": " + lhs + " " + rhs);
            }

            // makes the cascade below easier
            if (lhsKind.compareTo(rhsKind) > 0) {
                State tmp = lhs;
                lhs = rhs;
                rhs = tmp;
            }
        }

        if (lhs.equals(State.UNDEFINED)) {
            assert !rhs.equals(State.UNDEFINED);
            return State.UNDEFINED;
        }
        if (lhs.equals(State.UNKNOWN_TYPE)) {
            // rhs is null or ref type
            return State.UNKNOWN_TYPE;
        }
        if (lhs.equals(State.NULL)) {
            assert rhs instanceof State.OfType;
            return rhs;
        }
        if (lhs.equals(State.UNINITIALIZED_THIS)) {
            // can't merge with anything else
            return State.UNDEFINED;
        }
        if (lhs.equals(State.WIDE)) {
            assert rhs instanceof State.OfType;
            // wide literal is narrowed to the given type
            return rhs;
        }

        if (lhs instanceof State.Narrow) {
            if (rhs instanceof State.Narrow) {
                return mergeWithOp((State.Narrow) lhs, (State.Narrow) rhs, operation);
            } else {
                assert rhs instanceof State.OfType;
                if (((State.OfType) rhs).getOnlyType().equals(Type.FLOAT_TYPE)) {
                    return rhs;
                }
                State.Narrow rightNarrow = State.Narrow.forType(((State.OfType) rhs).getOnlyType());
                State.Narrow merged = mergeWithOp((State.Narrow) lhs, rightNarrow, operation);

                // finally, widen to the next largest *integral* State.OfType.
                if (State.Narrow.BOOLEAN.contains(merged)) {
                    return State.OfType.BOOLEAN;
                } else if (State.Narrow.BYTE.contains(merged)) {
                    return State.OfType.BYTE;
                } else if (State.Narrow.SHORT.contains(merged)) {
                    if (State.Narrow.CHAR.contains(merged)) {
                        // okay wtf. this happens when merging a boolean (range 0..1) with a literal that is both in
                        // the short and char range. We can't properly merge this, and this is rare enough that I
                        // won't make another state.

                        // this solution is very iffy, because it is the only case where type ∩ narrow -> narrow.
                        // This could lead to a loop if not careful, but since narrow.merge converges, I *think* this
                        // should be fine.
                        return merged;
                    } else {
                        return State.OfType.SHORT;
                    }
                } else if (State.Narrow.CHAR.contains(merged)) {
                    return State.OfType.CHAR;
                } else {
                    return State.OfType.INT;
                }
            }
        }

        if (lhs instanceof State.OfType) {
            if (lhs.equals(rhs) &&
                (operation == null ||
                 operation == BinaryOperation.Type.OR_INT ||
                 operation == BinaryOperation.Type.AND_INT ||
                 operation == BinaryOperation.Type.XOR_INT)) {
                return lhs;
            }

            assert rhs instanceof State.OfType : rhs; // _TYPE are always last in the Kind enum
            TypeSet lhsTypes = ((State.OfType) lhs).getTypes();
            TypeSet rhsTypes = ((State.OfType) rhs).getTypes();
            if (((State.OfType) lhs).isReference()) {
                assert ((State.OfType) rhs).isReference();

                // we don't try to merge to the supertype right now, since this is futile for external types anyway.
                // It's better to try to build the rest of the verifier to handle this properly anyway.

                TypeSet union = TypeSet.union(lhsTypes, rhsTypes);
                if (union.equals(lhsTypes)) {
                    return lhs;
                } else if (union.equals(rhsTypes)) {
                    return rhs;
                } else {
                    State.OfType next = new State.OfType(union);
                    next.markParent(lhs);
                    next.markParent(rhs);
                    return next;
                }
            } else if (Types.isNarrowPrimitiveType(lhsTypes.getSingleType())) {
                assert Types.isNarrowPrimitiveType(rhsTypes.getSingleType());

                if (lhsTypes.getSingleType().equals(Type.FLOAT_TYPE) ||
                    rhsTypes.getSingleType().equals(Type.FLOAT_TYPE)) {
                    // it's not possible to merge floats with other primitive types
                    return State.UNDEFINED;
                }

                State.Narrow lNarrow = State.Narrow.forType(lhsTypes.getSingleType());
                State.Narrow rNarrow = State.Narrow.forType(rhsTypes.getSingleType());
                State.Narrow merged = mergeWithOp(lNarrow, rNarrow, operation);
                if (lNarrow.contains(merged)) {
                    return lhs;
                } else if (rNarrow.contains(merged)) {
                    return rhs;
                } else {
                    if (operation == null) {
                        // this should only happen for (short / byte) ∩ char
                        assert lhsTypes.getSingleType().equals(Type.SHORT_TYPE) ||
                               rhsTypes.getSingleType().equals(Type.SHORT_TYPE) ||
                               lhsTypes.getSingleType().equals(Type.BYTE_TYPE) ||
                               rhsTypes.getSingleType().equals(Type.BYTE_TYPE):
                                lhs + " " + rhs + " " + merged;
                        assert lhsTypes.getSingleType().equals(Type.CHAR_TYPE) ||
                               rhsTypes.getSingleType().equals(Type.CHAR_TYPE) :
                                lhs + " " + rhs + " " + merged;
                    }
                    return State.OfType.INT;
                }
            } else {
                assert Types.isWidePrimitiveType(lhsTypes.getSingleType());
                return State.UNDEFINED;
            }
        }

        throw new AssertionError(lhs);
    }

    @RequiredArgsConstructor
    private enum Kind {
        UNDEFINED(null),

        UNKNOWN(LocalVariable.Type.REFERENCE),
        NULL(LocalVariable.Type.REFERENCE),
        UNINIT_THIS(LocalVariable.Type.REFERENCE),
        REF_TYPE(LocalVariable.Type.REFERENCE),

        NARROW(LocalVariable.Type.NARROW),
        NARROW_TYPE(LocalVariable.Type.NARROW),

        WIDE(LocalVariable.Type.WIDE),
        WIDE_TYPE(LocalVariable.Type.WIDE);

        static Kind forState(State state) {
            if (state.equals(State.UNDEFINED)) { return Kind.UNDEFINED; }
            if (state.equals(State.UNKNOWN_TYPE)) { return Kind.UNKNOWN; }
            if (state.equals(State.NULL)) { return Kind.NULL; }
            if (state.equals(State.UNINITIALIZED_THIS)) { return Kind.UNINIT_THIS; }
            if (state instanceof State.OfType) {
                if (((State.OfType) state).isReference()) {
                    return Kind.REF_TYPE;
                } else if (Types.isNarrowPrimitiveType(((State.OfType) state).getOnlyType())) {
                    return Kind.NARROW_TYPE;
                } else if (Types.isWidePrimitiveType(((State.OfType) state).getOnlyType())) {
                    return Kind.WIDE_TYPE;
                } else {
                    throw new AssertionError("Non-value type " + state);
                }
            }
            if (state instanceof State.Narrow) { return Kind.NARROW; }
            if (state.equals(State.WIDE)) { return Kind.WIDE; }
            throw new AssertionError(state);
        }

        @Nullable final LocalVariable.Type type;
    }
}
