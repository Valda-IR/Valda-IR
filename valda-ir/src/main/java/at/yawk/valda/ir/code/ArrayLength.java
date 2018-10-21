package at.yawk.valda.ir.code;

import java.util.Collection;
import java.util.Collections;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * @author yawkat
 */
@Builder
@EqualsAndHashCode(callSuper = false)
@ToString
@Getter
@Setter
public final class ArrayLength extends Instruction {
    public static final Slot TARGET = Slot.single("target", ArrayLength::getTarget, ArrayLength::setTarget);
    public static final Slot OPERAND = Slot.single("operand", ArrayLength::getOperand, ArrayLength::setOperand);

    @NonNull private LocalVariable target;
    @NonNull private LocalVariable operand;

    @Override
    public Collection<Slot> getInputSlots() {
        return Collections.singleton(OPERAND);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return Collections.singleton(TARGET);
    }
}
