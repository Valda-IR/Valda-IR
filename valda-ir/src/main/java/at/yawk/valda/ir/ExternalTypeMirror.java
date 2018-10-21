package at.yawk.valda.ir;

import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.NonNull;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@ToString(of = "type")
public final class ExternalTypeMirror extends TypeMirror {
    // we do some special casing for an external java.lang.Object to avoid creating unnecessary undeclared methods
    static final Set<MemberSignature> JAVA_LANG_OBJECT_METHODS = ImmutableSet.of(
            new MemberSignature("<init>", Type.getMethodType("()V")),
            new MemberSignature("getClass", Type.getMethodType("()Ljava/lang/Class;")),
            new MemberSignature("hashCode", Type.getMethodType("()I")),
            new MemberSignature("equals", Type.getMethodType("(Ljava/lang/Object;)Z")),
            new MemberSignature("clone", Type.getMethodType("()Ljava/lang/Object;")),
            new MemberSignature("toString", Type.getMethodType("()Ljava/lang/String;")),
            new MemberSignature("notify", Type.getMethodType("()V")),
            new MemberSignature("notifyAll", Type.getMethodType("()V")),
            new MemberSignature("wait", Type.getMethodType("(J)V")),
            new MemberSignature("wait", Type.getMethodType("(JI)V")),
            new MemberSignature("wait", Type.getMethodType("()V")),
            new MemberSignature("finalize", Type.getMethodType("()V"))
    );

    private static final Type ANNOTATION_DEFAULT_TYPE = Type.getObjectType("dalvik/annotation/AnnotationDefault");

    private final Type type;
    @NonNull private TriState isInterface = TriState.MAYBE;

    ExternalTypeMirror(Classpath classpath, Type type) {
        super(classpath);
        this.type = type;
        if (type.getSort() != Type.OBJECT || type.equals(Types.OBJECT)) {
            isInterface = TriState.FALSE;
        }
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean isInterface() {
        return isInterface.asBoolean(() -> {
            throw new IllegalStateException("Don't know if this type is an interface");
        });
    }

    public void setInterface(boolean isInterface) {
        if (this.isInterface != TriState.MAYBE && this.isInterface.asBoolean() != isInterface) {
            throw new IllegalArgumentException("Mismatched setInterface call");
        }
        this.isInterface = TriState.valueOf(isInterface);
    }

    @NonNull
    @Override
    public ExternalMethodMirror method(String name, Type type, TriState isStatic) {
        return (ExternalMethodMirror) super.method(name, type, isStatic);
    }

    @NonNull
    @Override
    public ExternalMethodMirror method(MemberSignature signature, TriState isStatic) {
        for (TypeReference.MethodDeclaringType reference :
                getReferences().listReferences(TypeReference.MethodDeclaringType.class)) {
            ExternalMethodMirror method = (ExternalMethodMirror) reference.getMethod();
            if (method.getSignature().equals(signature)) {
                if (isStatic != TriState.MAYBE) {
                    if (method.isStatic != TriState.MAYBE && method.isStatic != isStatic) {
                        throw new NoSuchMemberException("Found matching method, but static modifier does not match",
                                                        this,
                                                        signature,
                                                        isStatic);
                    }
                    method.isStatic = isStatic;
                }
                return method;
            }
        }
        if (this.type.equals(Types.OBJECT)) {
            if (!JAVA_LANG_OBJECT_METHODS.contains(signature)) {
                throw new NoSuchMemberException("java.lang.Object does not have a method of this name and type",
                                                this,
                                                signature,
                                                isStatic);
            }
        }
        MutationGuard.check();
        // this implicitly creates a reference too
        return new ExternalMethodMirror(getClasspath(), this, signature.getName(), signature.getType(), isStatic);
    }

    @NonNull
    @Override
    public ExternalMethodMirror annotationMethod(String name, List<Type> possibleReturnTypes) {
        MutationGuard.check();

        if (type.equals(ANNOTATION_DEFAULT_TYPE) && name.equals("value")) {
            possibleReturnTypes = Collections.singletonList(Type.getType(Annotation.class));
        }

        for (TypeReference.MethodDeclaringType reference :
                getReferences().listReferences(TypeReference.MethodDeclaringType.class)) {
            ExternalMethodMirror method = (ExternalMethodMirror) reference.getMethod();
            if (method.getName().equals(name) && method.getParameters().isEmpty()) {
                if (TriState.TRUE == method.isStatic) {
                    throw new NoSuchMemberException("Found annotation method, but is static",
                                                    this,
                                                    new MemberSignature(name, type),
                                                    TriState.FALSE);
                }
                method.isStatic = TriState.FALSE;
                method.intersectReturnTypes(possibleReturnTypes, this);
                return method;
            }
        }
        if (this.type.equals(Types.OBJECT)) {
            MemberSignature signature = new MemberSignature(name, type);
            if (!JAVA_LANG_OBJECT_METHODS.contains(signature)) {
                throw new NoSuchMemberException("java.lang.Object does not have a method of this name and type",
                                                this,
                                                signature,
                                                TriState.FALSE);
            }
        }
        // this implicitly creates a reference too
        return new ExternalMethodMirror(
                getClasspath(),
                this,
                name,
                Collections.emptyList(),
                possibleReturnTypes,
                TriState.FALSE
        );
    }

    @NonNull
    @Override
    public ExternalFieldMirror field(MemberSignature signature, TriState isStatic)
            throws NoSuchMemberException {
        for (TypeReference.FieldDeclaringType reference :
                getReferences().listReferences(TypeReference.FieldDeclaringType.class)) {
            ExternalFieldMirror field = (ExternalFieldMirror) reference.getField();
            if (field.getSignature().equals(signature)) {
                if (isStatic != TriState.MAYBE) {
                    if (field.isStatic != TriState.MAYBE && field.isStatic != isStatic) {
                        throw new NoSuchMemberException("Found matching field but mismatched static modifier",
                                                        this,
                                                        signature,
                                                        isStatic);
                    }
                    field.isStatic = isStatic;
                }
                return field;
            }
        }
        if (this.type.equals(Types.OBJECT)) {
            throw new NoSuchMemberException(
                    "java.lang.Object does not have fields static=" + isStatic, this, signature, isStatic);
        }
        MutationGuard.check();
        // this implicitly creates a reference too
        return new ExternalFieldMirror(
                getClasspath(),
                this,
                getClasspath().getTypeMirror(signature.getType()),
                signature.getName(),
                isStatic);
    }
}
