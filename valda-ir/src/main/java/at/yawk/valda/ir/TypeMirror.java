package at.yawk.valda.ir;

import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
public abstract class TypeMirror implements ReferenceTarget<TypeReference> {
    private final Classpath classpath;
    @Getter private final References<TypeReference> references = References.create(TypeReference.class);

    TypeMirror(Classpath classpath) {
        this.classpath = classpath;
    }

    Classpath getClasspath() {
        return classpath;
    }

    public final ArrayTypeMirror getArrayType() {
        return (ArrayTypeMirror) classpath.getTypeMirror(Type.getType("[" + getType().getDescriptor()));
    }

    public abstract Type getType();

    public abstract boolean isInterface();

    /**
     * Gets or creates a method of the given signature, if possible.
     *
     * <ul>
     * <li>If the method is already present, it is returned.</li>
     * <li>For external types, if the method is not present yet, it is created.</li>
     * <li>For local types, if the method is not present yet but there is a matching supertype method, an
     * {@link LocalMethodMirror#isDeclared() undeclared} method is created and returned</li>
     * <li>For local types, if the method is not present and there is no matching supertype method,
     * {@link NoSuchMemberException} is thrown.</li>
     * </ul>
     *
     * @param isStatic if {@link TriState#MAYBE}, the 'staticness' of this method is unknown. This can happen for method
     *                 references from annotations.
     *
     * @throws NoSuchMemberException if no method of this signature can possibly exist or a method of the same
     *                                signature is already present but does not match the {@code isStatic} parameter.
     */
    @NonNull
    public MethodMirror method(String name, Type type, TriState isStatic)
            throws NoSuchMemberException {
           return method(new MemberSignature(name, type), isStatic);
    }

    @NonNull
    public abstract MethodMirror method(MemberSignature signature, TriState isStatic)
            throws NoSuchMemberException;

    /**
     * Similar to {@link #method(String, Type, TriState)}. For annotation methods, the parser can
     * sometimes not determine the exact return type, so this is as good as it gets.
     *
     * The return type has to match <i>exactly</i> a type in the given list, except for {@link Enum}.
     */
    @NonNull
    public abstract MethodMirror annotationMethod(String name, List<Type> possibleReturnTypes)
            throws NoSuchMemberException;

    /**
     * @see #method(String, Type, TriState)
     */
    @NonNull
    public abstract FieldMirror field(MemberSignature signature, TriState isStatic)
            throws NoSuchMemberException;

    /**
     * @see #field(MemberSignature, TriState)
     */
    @NonNull
    public FieldMirror field(String name, Type type, TriState isStatic)
            throws NoSuchMemberException {
        return field(new MemberSignature(name, type), isStatic);
    }
}
