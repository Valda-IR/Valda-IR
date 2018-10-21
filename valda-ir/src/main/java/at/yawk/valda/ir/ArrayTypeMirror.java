package at.yawk.valda.ir;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

/**
 * {@link TypeMirror} for an array type.
 *
 * @author yawkat
 */
public final class ArrayTypeMirror extends TypeMirror {
    private final TypeReference.ArrayComponentType componentType;

    private final Map<MemberSignature, ExternalMethodMirror> methods = new HashMap<>();

    ArrayTypeMirror(Classpath classpath, TypeMirror componentType) {
        super(classpath);
        this.componentType = new TypeReference.ArrayComponentType(componentType, this);
        componentType.getReferences().add(this.componentType);
    }

    public TypeMirror getComponentType() {
        return componentType.getReferencedType();
    }

    @Override
    public Type getType() {
        return Type.getType("[" + componentType.getReferencedType().getType().getDescriptor());
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @NonNull
    @Override
    public FieldMirror field(MemberSignature signature, TriState isStatic)
            throws NoSuchMemberException {
        throw new NoSuchMemberException("Arrays have no fields (.length is a special opcode!)",
                                        this,
                                        signature,
                                        isStatic);
    }

    @NonNull
    @Override
    public MethodMirror method(MemberSignature signature, TriState isStatic)
            throws NoSuchMemberException {
        if (isStatic == TriState.TRUE) {
            throw new NoSuchMemberException("Arrays have no static methods", this, signature, TriState.TRUE);
        }
        return methods.computeIfAbsent(signature, s -> {
            if (ExternalTypeMirror.JAVA_LANG_OBJECT_METHODS.contains(s)) {
                return new ExternalMethodMirror(getClasspath(),
                                                this,
                                                signature.getName(),
                                                signature.getType(),
                                                TriState.FALSE);
            } else {
                throw new NoSuchMemberException("Arrays have no methods except for the ones on java.lang.Object",
                                                this,
                                                signature, isStatic);
            }
        });
    }

    @NonNull
    @Override
    public MethodMirror annotationMethod(String name, List<Type> possibleReturnTypes)
            throws NoSuchMemberException {
        throw new NoSuchMemberException("Not an annotation",
                                        this,
                                        new MemberSignature(name, Type.getMethodType("()V")), TriState.FALSE);
    }

    @Override
    public String toString() {
        return "ArrayTypeMirror{" + getComponentType() + "}";
    }
}
