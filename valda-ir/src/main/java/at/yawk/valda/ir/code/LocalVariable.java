package at.yawk.valda.ir.code;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/**
 * @author yawkat
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalVariable {
    @NonNull private final Type type;
    @NonNull private final String name;

    public static LocalVariable create(Type type, String name) {
        return new LocalVariable(type, name);
    }

    private static String randomName() {
        return UUID.randomUUID().toString();
    }

    public static LocalVariable reference() {
        return reference(randomName());
    }

    public static LocalVariable reference(String name) {
        return new LocalVariable(Type.REFERENCE, name);
    }

    public static LocalVariable narrow() {
        return narrow(randomName());
    }

    public static LocalVariable narrow(String name) {
        return new LocalVariable(Type.NARROW, name);
    }

    public static LocalVariable wide() {
        return wide(randomName());
    }

    public static LocalVariable wide(String name) {
        return new LocalVariable(Type.WIDE, name);
    }

    public enum Type {
        NARROW,
        WIDE,
        REFERENCE,
    }
}
