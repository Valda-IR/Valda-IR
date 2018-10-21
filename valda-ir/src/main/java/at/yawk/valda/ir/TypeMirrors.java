package at.yawk.valda.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;

/**
 * Various utilities related to {@link TypeMirror} instances.
 *
 * @author yawkat
 */
@UtilityClass
public final class TypeMirrors {
    /**
     * Determine if the second parameter is a subtype of the first parameter (i.e. is assignable to the first
     * parameter). If both types are identical, {@link TriState#TRUE} is returned.
     */
    @NonNull
    public static TriState isSupertype(@NonNull TypeMirror superType, @NonNull TypeMirror subType) {
        if (superType.equals(subType)) {
            return TriState.TRUE;
        }
        if (Types.isPrimitiveType(superType.getType()) || Types.isPrimitiveType(subType.getType())) {
            return TriState.FALSE;
        }
        if (superType.getType().equals(Types.OBJECT)) {
            return TriState.TRUE;
        }

        if (subType instanceof ArrayTypeMirror) {
            if (superType instanceof ArrayTypeMirror) {
                return isSupertype(
                        ((ArrayTypeMirror) superType).getComponentType(),
                        ((ArrayTypeMirror) subType).getComponentType()
                );
            } else {
                Type type = superType.getType();
                return type.equals(Types.SERIALIZABLE) || type.equals(Types.CLONEABLE) || type.equals(Types.OBJECT) ?
                        TriState.TRUE : TriState.FALSE;
            }
        } else if (subType instanceof ExternalTypeMirror) {
            if (superType instanceof LocalClassMirror || superType instanceof ArrayTypeMirror) {
                return TriState.FALSE;
            } else if (superType instanceof ExternalTypeMirror) {
                return subType.getType().equals(Types.OBJECT) ? TriState.FALSE : TriState.MAYBE;
            } else {
                throw new AssertionError();
            }
        } else if (subType instanceof LocalClassMirror) {
            boolean maybe = false;
            if (!(superType instanceof LocalClassMirror) || superType.isInterface()) {
                for (TypeMirror itf : ((LocalClassMirror) subType).getInterfaces()) {
                    TriState i = isSupertype(superType, itf);
                    if (i == TriState.TRUE) {
                        return TriState.TRUE;
                    } else if (i == TriState.MAYBE) {
                        maybe = true;
                    }
                }
            }
            TypeMirror sup = ((LocalClassMirror) subType).getSuperType();
            if (sup != null) {
                TriState i = isSupertype(superType, sup);
                if (i == TriState.TRUE) {
                    return TriState.TRUE;
                } else if (i == TriState.MAYBE) {
                    maybe = true;
                }
            }
            return maybe ? TriState.MAYBE : TriState.FALSE;
        } else {
            throw new AssertionError();
        }
    }

    /**
     * Get the effective parameter types a method receives when called.
     * <br>
     * <b>Note:</b> The returned list <i>will not</i> exactly match the parameter types passed to an
     * {@link at.yawk.valda.ir.code.Invoke Invoke} instruction. In particular, if the invoke instruction is of
     * {@link at.yawk.valda.ir.code.Invoke.Type#NEW_INSTANCE Type.NEW_INSTANCE}, the invoke instruction will not
     * contain a parameter entry for the {@code this} variable, but the list returned by this method will.
     */
    public static List<Type> getEffectiveParameterTypes(MethodMirror methodMirror) {
        return getParameterTypes(methodMirror, !methodMirror.isStatic());
    }

    /**
     * Get the parameter types a method receives.
     *
     * @param includeInstance If {@code true}, an instance of the declaring class will be added as the first
     *                        parameter. {@link MethodMirror#isStatic()} is not respected, you must include it in
     *                        your selection for this parameter.
     */
    public static List<Type> getParameterTypes(MethodMirror methodMirror, boolean includeInstance) {
        Type[] argumentTypes = methodMirror.getType().getArgumentTypes();
        if (!includeInstance) {
            return Arrays.asList(argumentTypes);
        } else {
            List<Type> result = new ArrayList<>(argumentTypes.length + 1);
            result.add(methodMirror.getDeclaringType().getType());
            Collections.addAll(result, argumentTypes);
            return result;
        }
    }
}
