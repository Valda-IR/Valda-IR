package at.yawk.valda.ir;

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Collection;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.objectweb.asm.Type;

/**
 * Utility methods for working with ASM {@link Type} instances.
 *
 * @author yawkat
 */
@UtilityClass
public final class Types {
    public static final Type OBJECT = Type.getType(Object.class);
    public static final Type THROWABLE = Type.getType(Throwable.class);
    public static final Type CLONEABLE = Type.getType(Cloneable.class);
    public static final Type SERIALIZABLE = Type.getType(Serializable.class);
    public static final Collection<Type> ARRAY_TYPES = ImmutableList.of(
            Type.getType(boolean[].class),
            Type.getType(byte[].class),
            Type.getType(short[].class),
            Type.getType(char[].class),
            Type.getType(int[].class),
            Type.getType(float[].class),
            Type.getType(long[].class),
            Type.getType(double[].class),
            Type.getType(Object[].class)
    );

    public static boolean isReferenceType(@NonNull Type type) {
        return isSimpleClassType(type) || isArrayType(type);
    }

    public static boolean isSimpleClassType(@NonNull Type type) {
        return type.getSort() == Type.OBJECT;
    }

    public static boolean isArrayType(@NonNull Type type) {
        return type.getSort() == Type.ARRAY;
    }

    public static boolean isPrimitiveType(@NonNull Type type) {
        return !isReferenceType(type) && !isVoidType(type) && !isMethodType(type);
    }

    public static boolean isVariableType(@NonNull Type type) {
        return !isMethodType(type) && !isVoidType(type);
    }

    public static boolean isVoidType(@NonNull Type type) {
        return type.getSort() == Type.VOID;
    }

    public static boolean isMethodType(@NonNull Type type) {
        return type.getSort() == Type.METHOD;
    }

    static String getPackage(String className) {
        return className.indexOf('.') == -1 ? "" : className.substring(0, className.lastIndexOf('.'));
    }

    public static String getPackage(Type type) {
        if (!Types.isReferenceType(type)) { throw new IllegalArgumentException("Not a reference type: " + type); }
        return getPackage(type.getClassName());
    }

    public static Type getComponentType(Type type) {
        if (type.getSort() != Type.ARRAY) { throw new IllegalArgumentException(type + " is not an array"); }
        return Type.getType(type.getDescriptor().substring(1));
    }

    public static boolean isNarrowPrimitiveType(Type type) {
        int sort = type.getSort();
        switch (sort) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
            case Type.FLOAT:
                return true;
            default:
                return false;
        }
    }

    public static boolean isWidePrimitiveType(Type type) {
        return type.getSort() == Type.LONG || type.getSort() == Type.DOUBLE;
    }
}
