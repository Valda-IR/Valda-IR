package at.yawk.valda.analyze.verifier;

import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeMirrors;
import at.yawk.valda.ir.Types;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@Slf4j
public abstract class State {
    /*
     * The "depth checks" that follow build up a tree of states that lead to a given state. This helps detect very
     * long merge chains. "Constant" states like NULL are excluded for simplicity.
     */

    private static final boolean DEPTH_CHECKS = log.isTraceEnabled();

    private int depth;
    private State parent1;
    private State parent2;

    void markParent(State parent) {
        if (!DEPTH_CHECKS) { return; }

        if (parent1 == null) {
            parent1 = parent;
            depth = parent.depth + 1;
        } else if (parent2 == null) {
            parent2 = parent;
            depth = Math.max(depth, parent.depth + 1);
        } else {
            throw new AssertionError();
        }
        checkParent();
    }

    private void checkParent() {
        if (!DEPTH_CHECKS) { return; }

        if (depth == 200) {
            StringBuilder builder = new StringBuilder();
            appendStateParents(builder, 0, this);
            log.warn("State has depth > 10:\n{}", builder);
        }
    }

    private static void appendStateParents(StringBuilder builder, int indent, State s) {
        for (int i = 0; i < indent; i++) {
            builder.append(' ');
        }
        builder.append(s).append('\n');
        if (s.parent1 != null) { appendStateParents(builder, indent + 1, s.parent1); }
        if (s.parent2 != null) { appendStateParents(builder, indent + 1, s.parent2); }
    }

    private State() {
    }

    public abstract boolean hasProperty(Property property);

    public abstract TriState isAssignableTo(Classpath classpath, Type type);

    public static final State NULL = new State() {
        @Override
        public boolean hasProperty(Property property) {
            switch (property) {
                case INTEGRAL:
                case WIDE:
                    return false;
                case NULL_OR_ZERO:
                case REFERENCE_OR_NULL:
                case INTEGRAL_REFERENCE_OR_NULL:
                case ARRAY_OR_NULL:
                    return true;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public TriState isAssignableTo(Classpath classpath, Type type) {
            return Types.isReferenceType(type) ? TriState.TRUE : TriState.FALSE;
        }

        @Override
        public String toString() {
            return "State.NULL";
        }
    };
    public static final State UNKNOWN_TYPE = new State() {
        @Override
        public boolean hasProperty(Property property) {
            switch (property) {
                case INTEGRAL:
                case WIDE:
                    return false;
                case REFERENCE_OR_NULL:
                case INTEGRAL_REFERENCE_OR_NULL:
                case ARRAY_OR_NULL:
                    return true;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public TriState isAssignableTo(Classpath classpath, Type type) {
            return Types.isReferenceType(type) ? TriState.TRUE : TriState.FALSE;
        }

        @Override
        public String toString() {
            return "State.UnknownRef";
        }
    };
    public static final State UNDEFINED = new State() {
        @Override
        public boolean hasProperty(Property property) {
            return false;
        }

        @Override
        public TriState isAssignableTo(Classpath classpath, Type type) {
            return TriState.FALSE;
        }

        @Override
        public String toString() {
            return "State.UNDEFINED";
        }
    };
    public static final State WIDE = new State() {
        @Override
        public boolean hasProperty(Property property) {
            return property == Property.WIDE;
        }

        @Override
        public TriState isAssignableTo(Classpath classpath, Type type) {
            return type.getSort() == Type.LONG || type.getSort() == Type.DOUBLE ? TriState.TRUE : TriState.FALSE;
        }

        @Override
        public String toString() {
            return "State.Wide";
        }
    };

    @EqualsAndHashCode(callSuper = false)
    @Value
    public static class Narrow extends State {
        public static final Narrow BOOLEAN = new Narrow(0, 1, false);
        public static final Narrow BYTE = new Narrow(Byte.MIN_VALUE, Byte.MAX_VALUE, false);
        public static final Narrow SHORT = new Narrow(Short.MIN_VALUE, Short.MAX_VALUE, false);
        public static final Narrow CHAR = new Narrow(Character.MIN_VALUE, Character.MAX_VALUE, false);
        public static final Narrow INT = new Narrow(Integer.MIN_VALUE, Integer.MAX_VALUE, false);
        public static final Narrow FLOAT = new Narrow(Integer.MIN_VALUE, Integer.MAX_VALUE, true);

        private final int min;
        private final int max;
        private final boolean canBeFloat;

        static int roundDown(int bound) {
            if (bound < -1) {
                @SuppressWarnings("PointlessBitwiseExpression")
                int out = bound & ~(Integer.highestOneBit(~bound) - 1);
                assert out <= bound;
                return out;
            } else {
                return bound == -1 ? -1 : 0;
            }
        }

        static int roundUp(int bound) {
            if (bound > 0) {
                int out = ((Integer.highestOneBit(bound) - 1) << 1) | 1;
                assert out >= bound;
                return out;
            } else {
                return 0;
            }
        }

        public Narrow(int min, int max, boolean canBeFloat) {
            if (min > max) { throw new IllegalArgumentException("min > max: " + min + " > " + max); }
            this.min = roundDown(min);
            this.max = roundUp(max);
            this.canBeFloat = canBeFloat;
        }

        public static Narrow forValue(int value) {
            return new Narrow(value, value, true);
        }

        public static Narrow forType(Type type) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    return BOOLEAN;
                case Type.BYTE:
                    return BYTE;
                case Type.SHORT:
                    return SHORT;
                case Type.CHAR:
                    return CHAR;
                case Type.INT:
                    return INT;
                case Type.FLOAT:
                    return FLOAT;
                default:
                    throw new IllegalArgumentException("Not a narrow type: " + type);
            }
        }

        @Override
        public boolean hasProperty(Property property) {
            switch (property) {
                case INTEGRAL:
                case INTEGRAL_REFERENCE_OR_NULL:
                    return true;
                case ARRAY_OR_NULL:
                case REFERENCE_OR_NULL:
                case NULL_OR_ZERO:
                case WIDE:
                    return false;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public TriState isAssignableTo(Classpath classpath, Type type) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    return BOOLEAN.contains(this) ? TriState.TRUE : TriState.FALSE;
                case Type.BYTE:
                    return BYTE.contains(this) ? TriState.TRUE : TriState.FALSE;
                case Type.SHORT:
                    return SHORT.contains(this) ? TriState.TRUE : TriState.FALSE;
                case Type.CHAR:
                    return CHAR.contains(this) ? TriState.TRUE : TriState.FALSE;
                case Type.INT:
                    return TriState.TRUE;
                case Type.FLOAT:
                    return canBeFloat ? TriState.TRUE : TriState.FALSE;
                default:
                    return TriState.FALSE;
            }
        }

        @Override
        public String toString() {
            return "State.Narrow(" + min + ".." + max + ")";
        }

        public boolean contains(Narrow other) {
            return this.min <= other.min && this.max >= other.max;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    public static class OfType extends State {
        public static final OfType BOOLEAN = new OfType(Type.BOOLEAN_TYPE);
        public static final OfType BYTE = new OfType(Type.BYTE_TYPE);
        public static final OfType SHORT = new OfType(Type.SHORT_TYPE);
        public static final OfType CHAR = new OfType(Type.CHAR_TYPE);
        public static final OfType INT = new OfType(Type.INT_TYPE);
        public static final OfType FLOAT = new OfType(Type.FLOAT_TYPE);
        public static final OfType LONG = new OfType(Type.LONG_TYPE);
        public static final OfType DOUBLE = new OfType(Type.DOUBLE_TYPE);

        private final TypeSet types;

        public OfType(TypeSet types) {
            TypeSet normalized = types.mapNullable(t -> t.equals(Types.OBJECT) ? null : t);
            if (normalized == null) { normalized = TypeSet.create(Types.OBJECT); }

            this.types = normalized;
            assert types.isSingleType() || types.matches(Types::isReferenceType) == TriState.TRUE;
        }

        public OfType(Type type) {
            this(TypeSet.create(type));
        }

        public boolean isReference() {
            return !types.isSingleType() || Types.isReferenceType(getOnlyType());
        }

        public Type getOnlyType() {
            return types.getSingleType();
        }

        @Override
        public boolean hasProperty(Property property) {
            switch (property) {
                case INTEGRAL:
                    return types.isSingleType() &&
                           (getOnlyType().getSort() == Type.BOOLEAN ||
                            getOnlyType().getSort() == Type.BYTE ||
                            getOnlyType().getSort() == Type.SHORT ||
                            getOnlyType().getSort() == Type.CHAR ||
                            getOnlyType().getSort() == Type.INT);
                case INTEGRAL_REFERENCE_OR_NULL:
                    return !types.isSingleType() ||
                           (getOnlyType().getSort() != Type.FLOAT &&
                            getOnlyType().getSort() != Type.DOUBLE &&
                            getOnlyType().getSort() != Type.LONG);
                case ARRAY_OR_NULL:
                    return types.matches(Types::isArrayType) == TriState.TRUE;
                case REFERENCE_OR_NULL:
                    return types.matches(Types::isReferenceType) == TriState.TRUE;
                case NULL_OR_ZERO:
                    return false;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public TriState isAssignableTo(Classpath classpath, Type type) {
            if (!isReference() && Types.isPrimitiveType(type)) {
                if (type.equals(Type.INT_TYPE)) {
                    return getOnlyType().equals(Type.INT_TYPE) ||
                           getOnlyType().equals(Type.SHORT_TYPE) ||
                           getOnlyType().equals(Type.CHAR_TYPE) ||
                           getOnlyType().equals(Type.BYTE_TYPE) ||
                           getOnlyType().equals(Type.BOOLEAN_TYPE) ? TriState.TRUE : TriState.FALSE;
                } else if (type.equals(Type.SHORT_TYPE)) {
                    return getOnlyType().equals(Type.SHORT_TYPE) ||
                           getOnlyType().equals(Type.BYTE_TYPE) ||
                           getOnlyType().equals(Type.BOOLEAN_TYPE) ? TriState.TRUE : TriState.FALSE;
                } else if (type.equals(Type.CHAR_TYPE)) {
                    return getOnlyType().equals(Type.CHAR_TYPE) ||
                           getOnlyType().equals(Type.BYTE_TYPE) ||
                           getOnlyType().equals(Type.BOOLEAN_TYPE) ? TriState.TRUE : TriState.FALSE;
                } else if (type.equals(Type.BYTE_TYPE)) {
                    return getOnlyType().equals(Type.BYTE_TYPE) ||
                           getOnlyType().equals(Type.BOOLEAN_TYPE) ? TriState.TRUE : TriState.FALSE;
                }
            }
            TypeMirror needle = classpath.getTypeMirror(type);
            return types.matchesTri((Type t) -> TypeMirrors.isSupertype(needle, classpath.getTypeMirror(t)));
        }
    }

    /**
     * This is a special type that signifies the uninitialized {@code this} reference at the start of a constructor,
     * before the super constructor call.
     */
    public static final State UNINITIALIZED_THIS = new State() {
        @Override
        public boolean hasProperty(Property property) {
            switch (property) {
                case INTEGRAL:
                case ARRAY_OR_NULL:
                case NULL_OR_ZERO:
                    return false;
                case INTEGRAL_REFERENCE_OR_NULL:
                case REFERENCE_OR_NULL:
                    return true;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public TriState isAssignableTo(Classpath classpath, Type type) {
            // handled specially
            return TriState.FALSE;
        }

        @Override
        public String toString() {
            return "State.UNINITIALIZED_THIS";
        }
    };

    public enum Property {
        INTEGRAL,
        WIDE,
        INTEGRAL_REFERENCE_OR_NULL,
        ARRAY_OR_NULL,
        REFERENCE_OR_NULL,
        NULL_OR_ZERO,
    }
}
