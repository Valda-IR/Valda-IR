package at.yawk.valda.ir;

import lombok.Getter;

/**
 * @author yawkat
 */
public abstract class FieldMirror implements Member, ReferenceTarget<FieldReference> {
    final Classpath classpath;
    @Getter private final References<FieldReference> references = References.create(FieldReference.class);
    private final TypeReference.FieldDeclaringType declaringType;
    private final TypeReference.FieldType type;

    FieldMirror(Classpath classpath, TypeMirror declaringType, TypeMirror type) {
        this.type = new TypeReference.FieldType(type, this);
        this.declaringType = new TypeReference.FieldDeclaringType(declaringType, this);
        this.classpath = classpath;

        declaringType.getReferences().add(this.declaringType);
        type.getReferences().add(this.type);
    }

    public TypeMirror getType() {
        return type.getReferencedType();
    }

    @Override
    public TypeMirror getDeclaringType() {
        return declaringType.getReferencedType();
    }

    @Override
    public abstract boolean isStatic();

    @Override
    public abstract String getName();

    @Override
    public MemberSignature getSignature() {
        return new MemberSignature(getName(), getType().getType());
    }

    public final String getDebugDescriptor() {
        return getDeclaringType().getType() + "->" + getName() + ":" + getType();
    }
}
