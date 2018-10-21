package at.yawk.valda.ir;

import at.yawk.valda.ir.annotation.AnnotationHolder;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@ToString(of = "name")
public final class LocalClassMirror extends TypeMirror {
    @Getter @NonNull private String name;
    @Nullable private TypeReference.Extends extendsReference = null;
    @NonNull private final Map<TypeMirror, TypeReference.Implements> interfaces = new HashMap<>();
    @Getter @Setter @NonNull private Access access = Access.PUBLIC;

    @Getter private boolean isStatic = true;
    @Getter private boolean isFinal = false;
    @Getter private boolean isAbstract = false;
    @Getter private boolean isInterface = false;
    @Getter private boolean isEnum = false;
    @Getter private boolean isAnnotation = false;
    @Getter private boolean isSynthetic = false;

    /**
     * If {@literal true}, this class <i>may</i> be placed in a secondary dex file (multidex).
     */
    @Getter @Setter private boolean secondaryDex = false;

    @Getter
    private final AnnotationHolder.ClassAnnotationHolder annotations = new AnnotationHolder.ClassAnnotationHolder(this);

    LocalClassMirror(Classpath classpath, @NonNull String name, @Nullable TypeMirror superType) {
        super(classpath);
        this.name = name;
        setSuperType(superType);
    }

    public void setSuperType(@Nullable TypeMirror type) {
        if (this.extendsReference != null) {
            extendsReference.getReferencedType().getReferences().remove(extendsReference);
        }
        if (type == null) {
            this.extendsReference = null;
        } else {
            this.extendsReference = new TypeReference.Extends(type, this);
            type.getReferences().add(extendsReference);
        }
    }

    @Nullable
    public TypeMirror getSuperType() {
        return extendsReference == null ? null : extendsReference.getReferencedType();
    }

    public Set<TypeMirror> getInterfaces() {
        return Collections.unmodifiableSet(interfaces.keySet());
    }

    public void removeInterface(TypeMirror mirror) throws NoSuchElementException {
        TypeReference.Implements ref = interfaces.remove(mirror);
        if (ref == null) { throw new NoSuchElementException(); }
        mirror.getReferences().remove(ref);
    }

    /**
     * @return {@literal true} if the interface was added, {@literal false} if it was already present.
     */
    public boolean addInterface(TypeMirror typeMirror) {
        TypeReference.Implements ref = new TypeReference.Implements(typeMirror, this);
        if (interfaces.putIfAbsent(typeMirror, ref) != null) { return false; }
        typeMirror.getReferences().add(ref);
        return true;
    }

    /**
     * Get all methods that:
     *
     * <ul>
     * <li>Are declared on this class</li>
     * <li>Or are assumed to exist on an <i>external</i> supertype by
     * {@link #method(String, Type, TriState)}</li>
     * <li>Or are on a <i>local</i> supertype (recursively)</li>
     * </ul>
     *
     * The return value of this method may change at any time with operations that <i>look</i> like they would be
     * read-only.
     */
    public Iterable<LocalMethodMirror> getAllMethods() {
        Set<LocalMethodMirror> methods = new HashSet<>();
        Iterables.addAll(methods, getAllMethodsNoResolve());
        for (TypeMirror superType : getSuperTypes()) {
            if (superType instanceof LocalClassMirror) {
                for (LocalMethodMirror method : ((LocalClassMirror) superType).getAllMethods()) {
                    methods.add(method(method.getSignature(), TriState.valueOf(method.isStatic())));
                }
            }
        }
        return methods;
    }

    /**
     * List all methods that are either {@link LocalMember#isDeclared() declared} or were accessed as undeclared
     * members using {@link #method(String, Type, TriState)}. This list may change at any time even
     * when only apparently read-only operations are done.
     */
    private FluentIterable<LocalMethodMirror> getAllMethodsNoResolve() {
        return FluentIterable.from(getReferences().listReferences(TypeReference.MethodDeclaringType.class))
                .transform(d -> {
                    assert d != null;
                    return (LocalMethodMirror) d.getMethod();
                });
    }

    public List<LocalMethodMirror> getDeclaredMethods() {
        return getAllMethodsNoResolve().filter(LocalMethodMirror::isDeclared).toList();
    }

    /**
     * List all fields that are either {@link LocalMember#isDeclared() declared} or were accessed as undeclared
     * members using {@link #field(String, Type, TriState)}. This list may change at any time even
     * when only apparently read-only operations are done.
     */
    public FluentIterable<LocalFieldMirror> getAllFields() {
        return FluentIterable.from(getReferences().listReferences(TypeReference.FieldDeclaringType.class))
                .transform(d -> {
                    assert d != null;
                    return (LocalFieldMirror) d.getField();
                });
    }

    public List<LocalFieldMirror> getDeclaredFields() {
        return getAllFields().filter(LocalFieldMirror::isDeclared).toList();
    }

    public LocalMethodMirror addMethod(String name) {
        // constructor creates ref too
        LocalMethodMirror method = new LocalMethodMirror(getClasspath(), this, name, true);
        method.setStatic(false);
        return method;
    }

    public LocalFieldMirror addField(String name, TypeMirror type) {
        // constructor creates ref too
        return new LocalFieldMirror(getClasspath(), this, name, type, true, TriState.FALSE);
    }

    @Override
    public Type getType() {
        return getType0(name);
    }

    private static Type getType0(String name) {
        return Type.getObjectType(name.replace('.', '/'));
    }

    @Nullable
    private LocalMethodMirror getMethodOrNull(MemberSignature signature, TriState isStatic) {
        for (LocalMethodMirror method : getAllMethodsNoResolve()) {
            if (method.getSignature().equals(signature) &&
                (isStatic == TriState.MAYBE || method.isStatic() == isStatic.asBoolean())) {
                return method;
            }
        }
        return null;
    }

    private List<TypeMirror> getSuperTypes() {
        List<TypeMirror> superTypes = new ArrayList<>(getInterfaces().size() + 1);
        if (getSuperType() != null) {
            superTypes.add(getSuperType());
        }
        superTypes.addAll(getInterfaces());
        return superTypes;
    }

    @NonNull
    @Override
    public LocalMethodMirror method(String name, Type type, TriState isStatic)
            throws NoSuchMemberException {
        return (LocalMethodMirror) super.method(name, type, isStatic);
    }

    @NonNull
    @Override
    public LocalMethodMirror method(MemberSignature signature, TriState isStatic)
            throws NoSuchMemberException {
        LocalMethodMirror present = getMethodOrNull(signature, isStatic);
        if (present != null) { return present; }

        List<TypeMirror> superTypes = getSuperTypes();

        boolean canGhost = false;
        TriState ghostStatic = isStatic;
        for (TypeMirror superType : superTypes) {
            try {
                MethodMirror method = superType.method(signature, isStatic);
                canGhost = true;
                if (isStatic == TriState.MAYBE) {
                    try {
                        ghostStatic = TriState.valueOf(method.isStatic());
                    } catch (IllegalStateException ignored) {
                    }
                }
            } catch (NoSuchMemberException ignored) {
            }
        }

        if (canGhost) {
            MutationGuard.check();

            LocalMethodMirror methodMirror = new LocalMethodMirror(getClasspath(), this, signature.getName(), false);
            if (ghostStatic != TriState.MAYBE) {
                methodMirror.setStatic(ghostStatic.asBoolean());
            }
            Type returnType = signature.getType().getReturnType();
            if (!returnType.equals(Type.VOID_TYPE)) {
                methodMirror.setReturnType(getClasspath().getTypeMirror(returnType));
            }
            for (Type argType : signature.getType().getArgumentTypes()) {
                methodMirror.addParameter(getClasspath().getTypeMirror(argType));
            }
            return methodMirror;
        }

        throw new NoSuchMemberException("Local", this, signature, isStatic);
    }

    @NonNull
    @Override
    public LocalMethodMirror annotationMethod(String name, List<Type> possibleReturnTypes)
            throws NoSuchMemberException {
        if (!isAnnotation()) {
            throw new NoSuchMemberException("Not an annotation", this,
                                            new MemberSignature(name,
                                                                Type.getMethodType("()V")),
                                            TriState.FALSE);
        }
        for (LocalMethodMirror method : getAllMethodsNoResolve()) {
            if (method.getName().equals(name) && method.getParameters().isEmpty()) {
                if (method.isStatic()) {
                    throw new NoSuchMemberException("Method is static",
                                                    this,
                                                    new MemberSignature(name,
                                                                        Type.getMethodType("()V")),
                                                    TriState.FALSE);
                }
                Type returnType = method.getReturnType() == null ? Type.VOID_TYPE : method.getReturnType().getType();
                for (Type possibleReturnType : possibleReturnTypes) {

                    if (Objects.equals(returnType, possibleReturnType)) {
                        return method;
                    }
                    // Enum
                    if (possibleReturnType.equals(Type.getType(Enum.class)) &&
                        ExternalMethodMirror.isPossiblyEnum(getClasspath(), returnType)) {
                        return method;
                    }
                    // arrays of enum
                    // getElementType returns the lowest type! for [[LEnum;, it yields LEnum;, not [LEnum;
                    if (Types.isArrayType(possibleReturnType) &&
                        possibleReturnType.getElementType().equals(Type.getType(Enum.class)) &&
                        possibleReturnType.getDimensions() == returnType.getDimensions() &&
                        ExternalMethodMirror.isPossiblyEnum(getClasspath(), returnType.getElementType())) {
                        return method;
                    }
                }
                throw new NoSuchMemberException(
                        "Return types do not match (has " + returnType + ", expected one of " + possibleReturnTypes +
                        ")", this, new MemberSignature(name, Type.getMethodType("()V")), TriState.FALSE);
            }
        }
        throw new NoSuchMemberException("None found for types " + possibleReturnTypes,
                                        this, new MemberSignature(name, Type.getMethodType("()V")), TriState.FALSE);
    }

    @NonNull
    @Override
    public LocalFieldMirror field(MemberSignature signature, TriState isStatic)
            throws NoSuchMemberException {
        TypeMirror typeMirror = getClasspath().getTypeMirror(signature.getType());
        LocalFieldMirror present = getFieldOrNull(signature.getName(), typeMirror, isStatic);
        if (present != null) {
            return present;
        }
        List<TypeMirror> superTypes;
        if (TriState.FALSE == isStatic) {
            if (getSuperType() == null) {
                superTypes = Collections.emptyList();
            } else {
                superTypes = Collections.singletonList(getSuperType());
            }
        } else {
            superTypes = getSuperTypes();
        }
        for (TypeMirror superType : superTypes) {
            try {
                superType.field(signature.getName(), signature.getType(), isStatic);
            } catch (NoSuchMemberException e) {
                continue;
            }
            MutationGuard.check();
            return new LocalFieldMirror(getClasspath(), this, signature.getName(), typeMirror, false, isStatic);
        }
        throw new NoSuchMemberException("None found", this, signature, isStatic);
    }

    @NonNull
    @Override
    public LocalFieldMirror field(String name, Type type, TriState isStatic)
            throws NoSuchMemberException {
        return (LocalFieldMirror) super.field(name, type, isStatic);
    }

    @Nullable
    private LocalFieldMirror getFieldOrNull(String name, TypeMirror type, TriState isStatic) {
        for (LocalFieldMirror field : getAllFields()) {
            if (field.getName().equals(name) && field.getType().equals(type) &&
                (isStatic == TriState.MAYBE || field.isStatic() == isStatic.asBoolean())) {
                return field;
            }
        }
        return null;
    }

    public void setName(String name) {
        if (!name.equals(this.name)) {
            Type oldType = getType0(this.name);
            Type newType = getType0(name);
            getClasspath().updateType(this, oldType, newType);
            this.name = name;
        }
    }

    /*
     * Invariants:
     *
     * annotation -> interface
     * interface -> abstract
     * abstract -> !final
     * final -> !abstract
     */

    public void setStatic(boolean static_) {
        isStatic = static_;
    }

    public void setFinal(boolean final_) {
        if (final_ && isInterface) {
            throw new IllegalArgumentException("Interface cannot be final");
        }
        isFinal = final_;
        if (final_) {
            isAbstract = false;
        }
    }

    public void setAbstract(boolean abstract_) {
        isAbstract = abstract_;
        if (abstract_) {
            isFinal = false;
        }
    }

    public void setInterface(boolean interface_) {
        isInterface = interface_;
        if (interface_) {
            isEnum = false;
            setAbstract(true);
        }
    }

    public void setEnum(boolean enum_) {
        isEnum = enum_;
        if (enum_) {
            isInterface = false;
        }
    }

    public void setAnnotation(boolean annotation) {
        isAnnotation = annotation;
        if (annotation) {
            setInterface(true);
        }
    }

    public void setSynthetic(boolean synthetic) {
        isSynthetic = synthetic;
    }
}
