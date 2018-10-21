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
@Getter
@Setter
public final class Move extends Instruction {
    public static final Slot FROM = Slot.single("from", Move::getFrom, Move::setFrom);
    public static final Slot TO = Slot.single("to", Move::getTo, Move::setTo);

    @NonNull LocalVariable from;
    @NonNull LocalVariable to;

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(FROM);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(TO);
    }
}
