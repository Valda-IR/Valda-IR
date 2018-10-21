package at.yawk.valda.ir.annotation;

import at.yawk.valda.ir.MethodMirror;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * @author yawkat
 */
public abstract class AnnotationPath {
    public static final AnnotationPath ROOT = new AnnotationPath() {
        @Override
        public AnnotationMember find(AnnotationHolder<?> holder) {
            AnnotationMember root = holder.get();
            if (root == null) { throw new NoSuchElementException(); }
            return root;
        }
    };

    private AnnotationPath() {
    }

    @NonNull
    public abstract AnnotationMember find(AnnotationHolder<?> holder) throws NoSuchElementException;

    public AnnotationPath resolveValue(MethodMirror key) {
        return new AnnotationValue(this, key);
    }

    public AnnotationPath resolveArrayIndex(int index) {
        return new ArrayMember(this, index);
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class ArrayMember extends AnnotationPath {
        private final AnnotationPath parent;
        private final int index;

        @Override
        public AnnotationMember find(AnnotationHolder<?> holder) throws NoSuchElementException {
            return find(parent.find(holder));
        }

        AnnotationMember find(AnnotationMember parent) throws NoSuchElementException {
            if (!(parent instanceof AnnotationMember.Array)) { throw new NoSuchElementException(); }
            List<? extends AnnotationMember> values = ((AnnotationMember.Array) parent).getValues();
            if (index < 0 || index >= values.size()) { throw new NoSuchElementException(); }
            return values.get(index);
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class AnnotationValue extends AnnotationPath {
        private final AnnotationPath parent;
        private final MethodMirror member;

        @Override
        public AnnotationMember find(AnnotationHolder<?> holder) throws NoSuchElementException {
            return find(parent.find(holder));
        }

        AnnotationMember find(AnnotationMember parent) throws NoSuchElementException {
            if (!(parent instanceof Annotation)) { throw new NoSuchElementException(); }
            Map<MethodMirror, AnnotationMember> values = ((Annotation) parent).getValues();
            AnnotationMember member = values.get(this.member);
            if (member == null) { throw new NoSuchElementException(); }
            return member;
        }
    }
}
