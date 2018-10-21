package at.yawk.valda.ir.code;

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
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public final class UnaryOperation extends Instruction {
    public static final Slot SOURCE = Slot.single("source", UnaryOperation::getSource, UnaryOperation::setSource);
    public static final Slot DESTINATION = Slot.single(
            "destination", UnaryOperation::getDestination, UnaryOperation::setDestination);

    @NonNull private final Type type;
    @NonNull private LocalVariable source;
    @NonNull private LocalVariable destination;

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(SOURCE);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(DESTINATION);
    }

    @Getter
    public enum Type {
        NEGATE_INT(LocalVariable.Type.NARROW),
        NEGATE_LONG(LocalVariable.Type.WIDE),
        NEGATE_FLOAT(LocalVariable.Type.NARROW),
        NEGATE_DOUBLE(LocalVariable.Type.WIDE),
        NOT_INT(LocalVariable.Type.NARROW),
        NOT_LONG(LocalVariable.Type.WIDE),
        INT_TO_LONG(LocalVariable.Type.WIDE, LocalVariable.Type.NARROW),
        INT_TO_FLOAT(LocalVariable.Type.NARROW),
        INT_TO_DOUBLE(LocalVariable.Type.WIDE, LocalVariable.Type.NARROW),
        LONG_TO_INT(LocalVariable.Type.NARROW, LocalVariable.Type.WIDE),
        LONG_TO_FLOAT(LocalVariable.Type.NARROW, LocalVariable.Type.WIDE),
        LONG_TO_DOUBLE(LocalVariable.Type.WIDE),
        FLOAT_TO_INT(LocalVariable.Type.NARROW),
        FLOAT_TO_LONG(LocalVariable.Type.WIDE, LocalVariable.Type.NARROW),
        FLOAT_TO_DOUBLE(LocalVariable.Type.WIDE, LocalVariable.Type.NARROW),
        DOUBLE_TO_INT(LocalVariable.Type.NARROW, LocalVariable.Type.WIDE),
        DOUBLE_TO_LONG(LocalVariable.Type.WIDE),
        DOUBLE_TO_FLOAT(LocalVariable.Type.NARROW, LocalVariable.Type.WIDE),
        INT_TO_BYTE(LocalVariable.Type.NARROW),
        INT_TO_CHAR(LocalVariable.Type.NARROW),
        INT_TO_SHORT(LocalVariable.Type.NARROW);

        private final LocalVariable.Type outType;
        private final LocalVariable.Type operandType;

        Type(LocalVariable.Type outType, LocalVariable.Type operandType) {
            this.outType = outType;
            this.operandType = operandType;
        }

        Type(LocalVariable.Type type) {
            this.outType = type;
            this.operandType = type;
        }

    }
}
