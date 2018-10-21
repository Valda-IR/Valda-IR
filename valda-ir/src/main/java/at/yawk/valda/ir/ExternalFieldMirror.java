package at.yawk.valda.ir;

import lombok.Getter;
import lombok.NonNull;

/**
 * @author yawkat
 */
public final class ExternalFieldMirror extends FieldMirror {
    @Getter private final String name;
    /**
     * If {@link TriState#MAYBE}, it is unknown whether this field is static or not
     */
    @NonNull TriState isStatic;

    ExternalFieldMirror(
            Classpath classpath,
            ExternalTypeMirror declaringType,
            TypeMirror type,
            String name,
            @NonNull TriState isStatic
    ) {
        super(classpath, declaringType, type);
        this.name = name;
        this.isStatic = isStatic;
    }

    @Override
    public ExternalTypeMirror getDeclaringType() {
        return (ExternalTypeMirror) super.getDeclaringType();
    }

    @Override
    public boolean isStatic() {
        return isStatic.asBoolean(() -> {
            throw new IllegalStateException("Not yet known whether this field is static");
        });
    }

    @Override
    public String toString() {
        return "ExternalFieldMirror{" + getDebugDescriptor() + "}";
    }
}
