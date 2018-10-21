package at.yawk.valda.ir.code;

import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.TypeMirrors;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@UtilityClass
public final class Instructions {
    @SuppressWarnings("RedundantIfStatement")
    public static boolean canThrow(Instruction instruction) {
        if (instruction instanceof ArrayLength) {
            return true;
        } else if (instruction instanceof ArrayLoadStore) {
            return true;
        } else if (instruction instanceof BinaryOperation) {
            switch (((BinaryOperation) instruction).getType()) {
                case DIV_INT:
                case REM_INT:
                case DIV_LONG:
                case REM_LONG:
                    return true;
                default:
                    return false;
            }
        } else if (instruction instanceof CheckCast) {
            return true;
        } else if (instruction instanceof Const) {
            // class init errors
            return ((Const) instruction).getValue() instanceof Const.Class;
        } else if (instruction instanceof FillArray) {
            return true;
        } else if (instruction instanceof Invoke) {
            return true;
        } else if (instruction instanceof InstanceOf) {
            // noclassdeffounderror
            return true;
        } else if (instruction instanceof LiteralBinaryOperation) {
            switch (((LiteralBinaryOperation) instruction).getType()) {
                case DIV:
                case REM:
                    return true;
                default:
                    return false;
            }
        } else if (instruction instanceof LoadStore) {
            return true;
        } else if (instruction instanceof Monitor) {
            return true;
        } else if (instruction instanceof NewArray) {
            return true; // OOM
        } else if (instruction instanceof Throw) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Same as {@link TypeMirrors#getEffectiveParameterTypes(MethodMirror)}, except that for
     * {@link Invoke.Type#NEW_INSTANCE} instructions this method will <i>exclude</i> the {@code this} reference.
     */
    public static List<Type> getInvokeParameterTypes(Invoke invoke) {
        return getInvokeParameterTypes(invoke.getMethod(), invoke.getType());
    }

    /**
     * @see #getInvokeParameterTypes(Invoke)
     */
    public static List<Type> getInvokeParameterTypes(MethodMirror method, Invoke.Type type) {
        return TypeMirrors.getParameterTypes(
                method, !method.isStatic() && type != Invoke.Type.NEW_INSTANCE);
    }

    public static Type getInvokeReturnType(Invoke invoke) {
        if (invoke.getType() == Invoke.Type.NEW_INSTANCE) {
            return invoke.getMethod().getDeclaringType().getType();
        } else {
            return invoke.getMethod().getType().getReturnType();
        }
    }

    /**
     * Return whether the given invoke instruction is a "special constructor invocation", meaning it is either a this
     * () call or a super constructor call.
     *
     * It should be noted that in java, invokespecial can also appear in a "normal" constructor invocation. However,
     * in our IR, instance creations are coalesced into {@link Invoke.Type#NEW_INSTANCE}.
     */
    public static boolean isSpecialConstructorInvoke(Invoke invoke) {
        return invoke.getMethod().isConstructor() && invoke.getType() == Invoke.Type.SPECIAL;
    }
}
