package at.yawk.valda.ir.code;

import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * @author yawkat
 */
@EqualsAndHashCode(callSuper = false)
@ToString
@Getter
@Setter
public final class InstanceOf extends Instruction {
    public static final Slot TARGET = Slot.single("target", InstanceOf::getTarget, InstanceOf::setTarget);
    public static final Slot OPERAND = Slot.single("operand", InstanceOf::getOperand, InstanceOf::setOperand);

    @NonNull private LocalVariable target;
    @NonNull private LocalVariable operand;
    @SuppressWarnings("NullableProblems")
    @NonNull private TypeReference.InstanceOfType type;

    @Builder
    InstanceOf(@NonNull LocalVariable target, @NonNull LocalVariable operand, TypeMirror type) {
        this.target = target;
        this.operand = operand;

        setType(type);
    }

    public void setType(TypeMirror type) {
        if (isClasspathLinked()) {
            unlinkClasspath();
        }
        this.type = SecretsHolder.secrets.instanceOfTypeReference(type, this);
        if (isClasspathLinked()) {
            linkClasspath();
        }
    }

    public TypeMirror getType() {
        return type.getReferencedType();
    }

    @Override
    void linkClasspath() {
        type.getReferencedType().getReferences().add(type);
    }

    @Override
    void unlinkClasspath() {
        type.getReferencedType().getReferences().remove(type);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(OPERAND);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(TARGET);
    }
}
