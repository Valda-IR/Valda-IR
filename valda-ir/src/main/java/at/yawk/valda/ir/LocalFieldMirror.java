package at.yawk.valda.ir;

import at.yawk.valda.ir.annotation.AnnotationHolder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * @author yawkat
 */
public final class LocalFieldMirror extends FieldMirror implements LocalMember {
    @Getter @Setter @NonNull private Access access = Access.PUBLIC;
    @NonNull private TriState isStatic;
    @Getter @Setter @NonNull private String name;

    @Getter @Setter private boolean declared;

    @Getter @Setter private boolean isFinal;
    @Getter @Setter private boolean isVolatile;
    @Getter @Setter private boolean isTransient;
    @Getter @Setter private boolean isSynthetic;
    @Getter @Setter private boolean isEnum;

    @Getter
    private final AnnotationHolder.FieldAnnotationHolder annotations = new AnnotationHolder.FieldAnnotationHolder(this);
    @Getter
    private final AnnotationHolder.FieldValueHolder defaultValue = new AnnotationHolder.FieldValueHolder(this);

    LocalFieldMirror(
            Classpath classpath,
            LocalClassMirror declaringType,
            @NonNull String name,
            TypeMirror type,
            boolean declared,
            @NonNull TriState isStatic
    ) {
        super(classpath, declaringType, type);
        this.name = name;
        this.declared = declared;
        this.isStatic = isStatic;
    }

    @Override
    public LocalClassMirror getDeclaringType() {
        return (LocalClassMirror) super.getDeclaringType();
    }

    @Override
    public boolean isStatic() {
        return isStatic.asBoolean();
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = TriState.valueOf(isStatic);
    }

    @Override
    public String toString() {
        return "LocalFieldMirror{" + getDebugDescriptor() + " declared=" + declared + "}";
    }
}
