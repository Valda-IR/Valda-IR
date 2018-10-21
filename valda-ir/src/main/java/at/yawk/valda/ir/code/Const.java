package at.yawk.valda.ir.code;

import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * @author yawkat
 */

@EqualsAndHashCode(callSuper = false, exclude = "reference")
@ToString
public final class Const extends Instruction {
    public static final Slot TARGET = Slot.single("target", Const::getTarget, Const::setTarget);

    @Getter @Setter @NonNull private LocalVariable target;
    @Getter @NonNull private Value value;
    @Nullable private TypeReference.ConstClass reference;

    private Const(@NonNull LocalVariable target, @NonNull Value value) {
        this.target = target;
        setValue(value);
    }

    public static Const create(@NonNull LocalVariable target, @NonNull Value value) {
        return new Const(target, value);
    }

    public static Const createWide(@NonNull LocalVariable target, double value) {
        return create(target, new Wide(value));
    }

    public static Const createWide(@NonNull LocalVariable target, long value) {
        return create(target, new Wide(value));
    }

    public static Const createNarrow(@NonNull LocalVariable target, int value) {
        return create(target, new Narrow(value));
    }

    public static Const createNarrow(@NonNull LocalVariable target, float value) {
        return create(target, new Narrow(value));
    }

    public static Const createString(@NonNull LocalVariable target, @NonNull java.lang.String value) {
        return create(target, new String(value));
    }

    public static Const createClass(@NonNull LocalVariable target, @NonNull TypeMirror value) {
        // this works even for void.class because void.class is not actually loaded as const, but from the Void.TYPE
        // field
        return create(target, new Class(value));
    }

    public static Const createNull(@NonNull LocalVariable target) {
        return create(target, NULL);
    }

    public void setValue(Value value) {
        if (reference != null) {
            unlinkClasspath();
        }
        this.value = value;
        if (value instanceof Class && isClasspathLinked()) {
            linkClasspath();
        }
    }

    @Override
    void linkClasspath() {
        if (value instanceof Class) {
            reference = SecretsHolder.secrets.newConst(((Class) value).getValue(), this);
            reference.getReferencedType().getReferences().add(reference);
        }
    }

    @Override
    void unlinkClasspath() {
        assert (reference != null) == (value instanceof Class);
        if (reference != null) {
            reference.getReferencedType().getReferences().remove(reference);
            reference = null;
        }
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of();
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(TARGET);
    }

    public void setValue(long value) {
        setValue(new Wide(value));
    }

    public void setValue(double value) {
        setValue(new Wide(value));
    }

    public void setValue(int value) {
        setValue(new Narrow(value));
    }

    public void setValueNull() {
        setValue(NULL);
    }

    public void setValue(float value) {
        setValue(new Narrow(value));
    }

    public void setValue(java.lang.String value) {
        setValue(new String(value));
    }

    public void setValue(TypeMirror value) {
        setValue(new Class(value));
    }

    public static abstract class Value {
        Value() {
        }
    }

    public static final Null NULL = new Null();

    @ToString
    public static final class Null extends Value {
        private Null() {
        }
    }

    @lombok.Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Narrow extends Value {
        private final int value;

        public Narrow(int value) {
            this.value = value;
        }

        public Narrow(float value) {
            this(Float.floatToIntBits(value));
        }
    }

    @lombok.Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Wide extends Value {
        private final long value;

        public Wide(long value) {
            this.value = value;
        }

        public Wide(double value) {
            this(Double.doubleToLongBits(value));
        }
    }

    @lombok.Value
    @EqualsAndHashCode(callSuper = false)
    public static final class String extends Value {
        @NonNull private final java.lang.String value;
    }

    @lombok.Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Class extends Value {
        @NonNull private final TypeMirror value;
    }
}
