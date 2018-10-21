package at.yawk.valda.ir;

import at.yawk.valda.ir.annotation.AnnotationHolder;
import at.yawk.valda.ir.annotation.AnnotationPath;
import lombok.Getter;

/**
 * @author yawkat
 */
@Getter
public abstract class MethodReference {
    private final MethodMirror referencedMethod;

    MethodReference(MethodMirror referencedMethod) {
        this.referencedMethod = referencedMethod;
    }

    @Getter
    public static class Invoke extends MethodReference {
        private final at.yawk.valda.ir.code.Invoke instruction;

        Invoke(MethodMirror referencedMethod, at.yawk.valda.ir.code.Invoke instruction) {
            super(referencedMethod);
            this.instruction = instruction;
        }
    }

    @Getter
    public static final class AnnotationMember extends MethodReference {
        private final AnnotationHolder<?> holder;
        private final AnnotationPath path;

        AnnotationMember(MethodMirror referencedMethod, AnnotationHolder<?> holder, AnnotationPath path) {
            super(referencedMethod);
            this.holder = holder;
            this.path = path;
        }
    }

    @Getter
    public static final class AnnotationKey extends MethodReference {
        private final AnnotationHolder<?> holder;
        private final AnnotationPath path;

        AnnotationKey(MethodMirror referencedMethod, AnnotationHolder<?> holder, AnnotationPath path) {
            super(referencedMethod);
            this.holder = holder;
            this.path = path;
        }
    }
}
