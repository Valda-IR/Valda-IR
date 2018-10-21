package at.yawk.valda.ir;

import at.yawk.valda.ir.annotation.AnnotationHolder;
import at.yawk.valda.ir.code.MethodBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
public final class LocalMethodMirror extends MethodMirror implements LocalMember {
    @Nullable private TypeReference.MethodReturnType returnType;
    @NonNull @Getter @Setter private String name;
    private final List<Parameter> parameters = new ArrayList<>();

    /**
     * @see LocalMember#isDeclared()
     */
    @Getter private final boolean declared;

    @Nullable @Getter private MethodBody body = null;

    @Getter @Setter @NonNull private Access access = Access.PUBLIC;
    @NonNull private TriState isStatic = TriState.MAYBE;
    @Getter @Setter private boolean isFinal = false;
    @Getter @Setter private boolean isSynchronized = false;
    @Getter @Setter private boolean isBridge = false;
    @Getter @Setter private boolean isVarargs = false;
    @Getter @Setter private boolean isNative = false;
    @Getter @Setter private boolean isAbstract = false;
    @Getter @Setter private boolean isStrictfp = false;
    @Getter @Setter private boolean isSynthetic = false;
    @Getter @Setter private boolean isDeclaredSynchronized = false;

    @Getter
    private final AnnotationHolder.MethodAnnotationHolder annotations =
            new AnnotationHolder.MethodAnnotationHolder(this);

    LocalMethodMirror(Classpath classpath, LocalClassMirror declaringType, @NonNull String name, boolean declared) {
        super(classpath, declaringType);
        this.name = name;
        this.declared = declared;
    }

    @Override
    public boolean isStatic() {
        return isStatic.asBoolean();
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = TriState.valueOf(isStatic);
    }

    @Override
    public LocalClassMirror getDeclaringType() {
        return (LocalClassMirror) super.getDeclaringType();
    }

    public void setReturnType(@Nullable TypeMirror returnType) {
        if (this.returnType != null) {
            this.returnType.getReferencedType().getReferences().remove(this.returnType);
        }
        if (returnType == null) {
            this.returnType = null;
        } else {
            this.returnType = new TypeReference.MethodReturnType(returnType, this);
            returnType.getReferences().add(this.returnType);
        }
    }

    public boolean isStaticInitializer() {
        return name.equals("<clinit>");
    }

    @Nullable
    public TypeMirror getReturnType() {
        return returnType == null ? null : returnType.getReferencedType();
    }

    @Override
    public Type getType() {
        TypeMirror returnType = getReturnType();
        return Type.getMethodType(returnType == null ? Type.VOID_TYPE : returnType.getType(),
                                  parameters.stream().map(p -> p.getType().getType()).toArray(Type[]::new));
    }

    @Override
    public String toString() {
        return "LocalMethodMirror{" + getDebugDescriptor() + " declared=" + declared +
               " body=" + body + "}";
    }

    @Override
    public boolean isPrivate() {
        return getAccess() == Access.PRIVATE;
    }

    @SuppressWarnings("deprecation")
    public void setBody(@Nullable MethodBody body) {
        if (this.body != null) {
            this.body._linkClasspath(Secrets.SECRETS, false);
        }
        this.body = body;
        if (this.body != null) {
            this.body._linkClasspath(Secrets.SECRETS, true);
        }
    }

    public Parameter addParameter(TypeMirror type, int index) {
        Parameter parameter = new Parameter(type);
        parameters.add(index, parameter);
        return parameter;
    }

    public Parameter addParameter(TypeMirror type) {
        Parameter parameter = new Parameter(type);
        parameters.add(parameter);
        return parameter;
    }

    public List<Parameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public class Parameter extends MethodMirror.Parameter {
        @Getter
        private final AnnotationHolder.ParameterAnnotationHolder annotations =
                new AnnotationHolder.ParameterAnnotationHolder(this);

        Parameter(TypeMirror type) {
            super(type);
        }

        public void remove() {
            if (!parameters.remove(this)) {
                throw new IllegalStateException();
            }
            annotations.set(null);
            removeRef();
        }
    }
}
