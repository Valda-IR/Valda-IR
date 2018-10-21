package at.yawk.valda.ir;

import lombok.Getter;
import lombok.NonNull;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
public abstract class MethodMirror implements Member, ReferenceTarget<MethodReference> {
    @Getter private final References<MethodReference> references = References.create(MethodReference.class);
    final Classpath classpath;
    @NonNull private final TypeReference.MethodDeclaringType declaringType;

    MethodMirror(Classpath classpath, TypeMirror declaringType) {
        this.classpath = classpath;
        this.declaringType = new TypeReference.MethodDeclaringType(declaringType, this);
        declaringType.getReferences().add(this.declaringType);
    }

    @Override
    public TypeMirror getDeclaringType() {
        return declaringType.getReferencedType();
    }

    @Override
    public abstract String getName();

    public abstract Type getType();

    @Override
    public MemberSignature getSignature() {
        return new MemberSignature(getName(), getType());
    }

    public final boolean isConstructor() {
        return getName().equals("<init>");
    }

    @Override
    public abstract boolean isStatic();

    public abstract boolean isPrivate();

    public class Parameter {
        @SuppressWarnings("NullableProblems")
        @NonNull private TypeReference.ParameterType type;

        Parameter(TypeMirror type) {
            setTypeImpl(type);
        }

        public TypeMirror getType() {
            return type.getReferencedType();
        }

        private void setTypeImpl(TypeMirror type) {
            this.type = new TypeReference.ParameterType(type, this);
            type.getReferences().add(this.type);
        }

        public void setType(TypeMirror type) {
            this.type.getReferencedType().getReferences().remove(this.type);
            setTypeImpl(type);
        }

        void removeRef() {
            type.getReferencedType().getReferences().remove(type);
        }
    }

    @Override
    public abstract String toString();

    public String getDebugDescriptor() {
        return getDeclaringType().getType() + "->" + getName() + getType();
    }
}
