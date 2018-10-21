package at.yawk.valda.ir.annotation;

import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.FieldReference;
import at.yawk.valda.ir.LocalClassMirror;
import at.yawk.valda.ir.LocalFieldMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.MethodReference;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * @author yawkat
 */
@SuppressWarnings("NewMethodNamingConvention")
public abstract class AnnotationHolder<Root extends AnnotationMember> {
    @Nullable private Root root = null;

    private final List<Runnable> unlink = new ArrayList<>();

    private AnnotationHolder() {
    }

    @NonNull
    public AnnotationMember find(AnnotationPath path) throws NoSuchElementException {
        return path.find(this);
    }

    @Nullable
    public Root get() {
        return root;
    }

    public void set(@Nullable Root root) {
        if (this.root != null) {
            for (Runnable runnable : unlink) {
                runnable.run();
            }
            unlink.clear();
        }
        this.root = root;
        if (root != null) {
            link(AnnotationPath.ROOT, root);
        }
    }

    private void link(AnnotationPath path, AnnotationMember member) {
        if (member instanceof AnnotationMember.Method) {
            MethodMirror method = ((AnnotationMember.Method) member).getMethod();
            MethodReference.AnnotationMember ref = SecretsHolder.secrets.methodAnnotationMember(method, this, path);
            method.getReferences().add(ref);
            unlink.add(() -> method.getReferences().remove(ref));
        } else if (member instanceof AnnotationMember.Field) {
            FieldMirror field = ((AnnotationMember.Field) member).getField();
            FieldReference.AnnotationMember ref = SecretsHolder.secrets.fieldAnnotationMember(field, this, path);
            field.getReferences().add(ref);
            unlink.add(() -> field.getReferences().remove(ref));
        } else if (member instanceof AnnotationMember.Type) {
            TypeMirror type = ((AnnotationMember.Type) member).getType();
            if (type != null) {
                TypeReference.AnnotationMember ref = SecretsHolder.secrets.typeAnnotationMember(type, this, path);
                type.getReferences().add(ref);
                unlink.add(() -> type.getReferences().remove(ref));
            }
        } else if (member instanceof AnnotationMember.Array) {
            List<? extends AnnotationMember> values = ((AnnotationMember.Array) member).getValues();
            for (int i = 0; i < values.size(); i++) {
                link(path.resolveArrayIndex(i), values.get(i));
            }
        } else if (member instanceof Annotation) {
            TypeMirror type = ((Annotation) member).getType();
            TypeReference.AnnotationType typeRef = SecretsHolder.secrets.annotationType(type, this, path);
            type.getReferences().add(typeRef);
            unlink.add(() -> type.getReferences().remove(typeRef));

            ((Annotation) member).getValues().forEach((k, v) -> {
                AnnotationPath subPath = path.resolveValue(k);
                MethodReference.AnnotationKey ref = SecretsHolder.secrets.methodAnnotationKey(k, this, subPath);
                k.getReferences().add(ref);
                unlink.add(() -> k.getReferences().remove(ref));

                link(subPath, v);
            });
        }
    }

    public static abstract class AnnotationAnnotationHolder extends AnnotationHolder<AnnotationMember.Array> {
        private AnnotationAnnotationHolder() {
        }

        public void addAnnotation(Annotation annotation) {
            set(new AnnotationMember.Array(
                    ImmutableList.<Annotation>builder()
                            .addAll(getAnnotations())
                            .add(annotation)
                            .build()
            ));
        }

        public List<Annotation> getAnnotations() {
            AnnotationMember.Array root = get();
            if (root == null) {
                return Collections.emptyList();
            } else {
                //noinspection unchecked
                return Collections.unmodifiableList((List<? extends Annotation>) root.getValues());
            }
        }

        @Override
        public void set(@Nullable AnnotationMember.Array array) {
            if (array != null) {
                for (AnnotationMember value : array.getValues()) {
                    if (!(value instanceof Annotation)) {
                        throw new IllegalArgumentException("Top-level array must only contain annotations");
                    }
                }
            }
            super.set(array);
        }
    }

    @RequiredArgsConstructor
    public static final class FieldAnnotationHolder extends AnnotationAnnotationHolder {
        private final LocalFieldMirror field;
    }

    @RequiredArgsConstructor
    public static final class MethodAnnotationHolder extends AnnotationAnnotationHolder {
        private final LocalMethodMirror method;
    }

    @RequiredArgsConstructor
    public static final class ParameterAnnotationHolder extends AnnotationAnnotationHolder {
        private final LocalMethodMirror.Parameter parameter;
    }

    @RequiredArgsConstructor
    public static final class ClassAnnotationHolder extends AnnotationAnnotationHolder {
        private final LocalClassMirror type;
    }

    @RequiredArgsConstructor
    public static final class FieldValueHolder extends AnnotationHolder<AnnotationMember> {
        private final LocalFieldMirror field;
    }
}
