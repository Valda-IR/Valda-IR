package at.yawk.valda.analyze;

import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.UnaryOperation;
import java.util.function.IntBinaryOperator;
import lombok.NonNull;
import org.eclipse.collections.api.block.function.primitive.IntToIntFunction;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;

/**
 * Simple {@link InterpreterAdapter} that does symbolic execution for narrow values. Does not support
 * non-{@link LocalVariable.Type#NARROW narrow} variables.
 *
 * @author yawkat
 */
public class IntInterpreter extends InterpreterAdapter<ImmutableIntSet> {
    @NonNull
    @Override
    public ImmutableIntSet merge(
            LocalVariable variable,
            @NonNull ImmutableIntSet oldValue,
            @NonNull ImmutableIntSet newValue
    ) {
        return oldValue.newWithAll(newValue);
    }

    @Override
    protected ImmutableIntSet constant(int narrow) {
        return IntSets.immutable.of(narrow);
    }

    @Override
    protected ImmutableIntSet unaryOperation(UnaryOperation.Type type, ImmutableIntSet operand) {
        IntToIntFunction map;
        switch (type) {
            case NEGATE_INT: {
                map = i -> -i;
                break;
            }
            case NOT_INT: {
                map = i -> ~i;
                break;
            }
            default: {
                return defaultValue();
            }
        }
        return operand.collectInt(map, IntSets.mutable.empty()).toImmutable();
    }

    @Override
    protected ImmutableIntSet binaryOperation(BinaryOperation.Type type, ImmutableIntSet lhs, ImmutableIntSet rhs) {
        IntBinaryOperator map;
        switch (type) {
            case ADD_INT: {
                map = (a, b) -> a + b;
                break;
            }
            case SUB_INT: {
                map = (a, b) -> a - b;
                break;
            }
            case MUL_INT: {
                map = (a, b) -> a * b;
                break;
            }
            case DIV_INT: {
                map = (a, b) -> a / b;
                break;
            }
            case REM_INT: {
                map = (a, b) -> a % b;
                break;
            }
            case AND_INT: {
                map = (a, b) -> a & b;
                break;
            }
            case OR_INT: {
                map = (a, b) -> a | b;
                break;
            }
            case XOR_INT: {
                map = (a, b) -> a ^ b;
                break;
            }
            case SHL_INT: {
                map = (a, b) -> a << b;
                break;
            }
            case SHR_INT: {
                map = (a, b) -> a >> b;
                break;
            }
            case USHR_INT: {
                map = (a, b) -> a >>> b;
                break;
            }
            default:
                return defaultValue();
        }
        MutableIntSet merged = IntSets.mutable.empty();
        lhs.forEach(a -> rhs.forEach(b -> merged.add(map.applyAsInt(a, b))));
        return merged.toImmutable();
    }

    @Override
    protected ImmutableIntSet literalBinaryOperation(
            LiteralBinaryOperation.Type type,
            ImmutableIntSet lhs,
            short rhs
    ) {
        switch (type) {
            case ADD:
                return lhs.collectInt(i -> i + rhs, IntSets.mutable.empty()).toImmutable();
            case RSUB:
                return lhs.collectInt(i -> rhs - i, IntSets.mutable.empty()).toImmutable();
            case MUL:
                return lhs.collectInt(i -> i * rhs, IntSets.mutable.empty()).toImmutable();
            case DIV:
                return lhs.collectInt(i -> i / rhs, IntSets.mutable.empty()).toImmutable();
            case REM:
                return lhs.collectInt(i -> i % rhs, IntSets.mutable.empty()).toImmutable();
            case AND:
                return lhs.collectInt(i -> i & rhs, IntSets.mutable.empty()).toImmutable();
            case OR:
                return lhs.collectInt(i -> i | rhs, IntSets.mutable.empty()).toImmutable();
            case XOR:
                return lhs.collectInt(i -> i ^ rhs, IntSets.mutable.empty()).toImmutable();
            case SHL:
                return lhs.collectInt(i -> i << rhs, IntSets.mutable.empty()).toImmutable();
            case SHR:
                return lhs.collectInt(i -> i << rhs, IntSets.mutable.empty()).toImmutable();
            case USHR:
                return lhs.collectInt(i -> i >>> rhs, IntSets.mutable.empty()).toImmutable();
            default:
                return defaultValue();
        }
    }

    @Override
    protected TriState branch(Branch.Type type, ImmutableIntSet lhs, ImmutableIntSet rhs) {
        switch (type) {
            case EQUAL: {
                if (lhs.size() == 1 && lhs.equals(rhs)) { return TriState.TRUE; }
                if (lhs.noneSatisfy(rhs::contains)) { return TriState.FALSE; } // completely disjoint
                return TriState.MAYBE;
            }
            case LESS_THAN: {
                if (lhs.max() < rhs.min()) { return TriState.TRUE; }
                if (lhs.min() > rhs.max()) { return TriState.FALSE; }
                if (lhs.size() == 1 && lhs.equals(rhs)) { return TriState.FALSE; }
                return TriState.MAYBE;
            }
            case GREATER_THAN: {
                if (lhs.max() < rhs.min()) { return TriState.FALSE; }
                if (lhs.min() > rhs.max()) { return TriState.TRUE; }
                if (lhs.size() == 1 && lhs.equals(rhs)) { return TriState.FALSE; }
                return TriState.MAYBE;
            }
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected IntSet switch_(ImmutableIntSet input, IntSet branches, int defaultMarker) {
        MutableIntSet reachable = IntSets.mutable.empty();
        input.forEach(i -> {
            if (branches.contains(i)) {
                reachable.add(i);
            } else {
                reachable.add(defaultMarker);
            }
        });
        return reachable;
    }

    @Override
    public boolean reevaluateUnreachable() {
        return false;
    }
}
