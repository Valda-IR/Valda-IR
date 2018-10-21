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
@Getter
@Setter
@Builder
@EqualsAndHashCode(callSuper = false)
@ToString
public final class BinaryOperation extends Instruction {
    public static final Slot DESTINATION = Slot.single(
            "destination", BinaryOperation::getDestination, BinaryOperation::setDestination);
    public static final Slot LHS = Slot.single("lhs", BinaryOperation::getLhs, BinaryOperation::setLhs);
    public static final Slot RHS = Slot.single("rhs", BinaryOperation::getRhs, BinaryOperation::setRhs);

    @NonNull private Type type;
    @NonNull private LocalVariable destination;
    @NonNull private LocalVariable lhs;
    @NonNull private LocalVariable rhs;

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(LHS, RHS);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(DESTINATION);
    }

    @Getter
    public enum Type {
        COMPARE_FLOAT_BIAS_L(LocalVariable.Type.NARROW),
        COMPARE_FLOAT_BIAS_G(LocalVariable.Type.NARROW),
        COMPARE_DOUBLE_BIAS_L(LocalVariable.Type.NARROW, LocalVariable.Type.WIDE, LocalVariable.Type.WIDE),
        COMPARE_DOUBLE_BIAS_G(LocalVariable.Type.NARROW, LocalVariable.Type.WIDE, LocalVariable.Type.WIDE),
        COMPARE_LONG(LocalVariable.Type.NARROW, LocalVariable.Type.WIDE, LocalVariable.Type.WIDE),

        ADD_INT(LocalVariable.Type.NARROW),
        SUB_INT(LocalVariable.Type.NARROW),
        MUL_INT(LocalVariable.Type.NARROW),
        DIV_INT(LocalVariable.Type.NARROW),
        REM_INT(LocalVariable.Type.NARROW),
        AND_INT(LocalVariable.Type.NARROW),
        OR_INT(LocalVariable.Type.NARROW),
        XOR_INT(LocalVariable.Type.NARROW),
        SHL_INT(LocalVariable.Type.NARROW),
        SHR_INT(LocalVariable.Type.NARROW),
        USHR_INT(LocalVariable.Type.NARROW),

        ADD_LONG(LocalVariable.Type.WIDE),
        SUB_LONG(LocalVariable.Type.WIDE),
        MUL_LONG(LocalVariable.Type.WIDE),
        DIV_LONG(LocalVariable.Type.WIDE),
        REM_LONG(LocalVariable.Type.WIDE),
        AND_LONG(LocalVariable.Type.WIDE),
        OR_LONG(LocalVariable.Type.WIDE),
        XOR_LONG(LocalVariable.Type.WIDE),
        SHL_LONG(LocalVariable.Type.WIDE, LocalVariable.Type.WIDE, LocalVariable.Type.NARROW),
        SHR_LONG(LocalVariable.Type.WIDE, LocalVariable.Type.WIDE, LocalVariable.Type.NARROW),
        USHR_LONG(LocalVariable.Type.WIDE, LocalVariable.Type.WIDE, LocalVariable.Type.NARROW),

        ADD_FLOAT(LocalVariable.Type.NARROW),
        SUB_FLOAT(LocalVariable.Type.NARROW),
        MUL_FLOAT(LocalVariable.Type.NARROW),
        DIV_FLOAT(LocalVariable.Type.NARROW),
        REM_FLOAT(LocalVariable.Type.NARROW),

        ADD_DOUBLE(LocalVariable.Type.WIDE),
        SUB_DOUBLE(LocalVariable.Type.WIDE),
        MUL_DOUBLE(LocalVariable.Type.WIDE),
        DIV_DOUBLE(LocalVariable.Type.WIDE),
        REM_DOUBLE(LocalVariable.Type.WIDE);

        private final LocalVariable.Type outType;
        private final LocalVariable.Type lhsType;
        private final LocalVariable.Type rhsType;

        Type(LocalVariable.Type type) {
            this.outType = type;
            this.lhsType = type;
            this.rhsType = type;
        }

        Type(LocalVariable.Type outType, LocalVariable.Type lhsType, LocalVariable.Type rhsType) {
            this.outType = outType;
            this.lhsType = lhsType;
            this.rhsType = rhsType;
        }
    }
}
