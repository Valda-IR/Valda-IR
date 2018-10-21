package at.yawk.valda.ir.annotation;

import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.TypeMirror;
import java.util.List;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

/**
 * @author yawkat
 */
public abstract class AnnotationMember {
    AnnotationMember() {
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Byte extends AnnotationMember {
        private final byte value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Short extends AnnotationMember {
        private final short value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Char extends AnnotationMember {
        private final char value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Int extends AnnotationMember {
        private final int value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Long extends AnnotationMember {
        private final long value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Float extends AnnotationMember {
        private final float value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Double extends AnnotationMember {
        private final double value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class String extends AnnotationMember {
        private final java.lang.String value;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Type extends AnnotationMember {
        /**
         * {@code null} for void.
         */
        @Nullable private final TypeMirror type;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Field extends AnnotationMember {
        private final FieldMirror field;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Method extends AnnotationMember {
        private final MethodMirror method;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Enum extends AnnotationMember {
        private final FieldMirror field;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Array extends AnnotationMember {
        private final List<? extends AnnotationMember> values;
    }

    @ToString
    public static class Null extends AnnotationMember {
        @SuppressWarnings("FieldNamingConvention")
        @Getter private static final Null instance = new Null();

        private Null() {
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Boolean extends AnnotationMember {
        private final boolean value;
    }
}
