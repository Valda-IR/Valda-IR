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
@EqualsAndHashCode(callSuper = false)
@ToString
public final class LiteralBinaryOperation extends Instruction {
    public static final Slot DESTINATION = Slot.single(
            "destination", LiteralBinaryOperation::getDestination, LiteralBinaryOperation::setDestination);
    public static final Slot LHS = Slot.single("lhs", LiteralBinaryOperation::getLhs, LiteralBinaryOperation::setLhs);

    @NonNull @Getter private final Type type;
    @NonNull @Getter @Setter private LocalVariable destination;
    @NonNull @Getter @Setter private LocalVariable lhs;
    @Getter @Setter private short rhs;

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(LHS);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(DESTINATION);
    }

    public enum Type {
        ADD,
        RSUB,
        MUL,
        DIV,
        REM,
        AND,
        OR,
        XOR,
        SHL,
        SHR,
        USHR,
    }
}
