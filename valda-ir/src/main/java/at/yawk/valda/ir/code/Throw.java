package at.yawk.valda.ir.code;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * @author yawkat
 */
@AllArgsConstructor(staticName = "create")
@EqualsAndHashCode(callSuper = false)
@ToString
@Getter
@Setter
public final class Throw extends TerminatingInstruction {
    public static final Slot EXCEPTION = Slot.single("exception", Throw::getException, Throw::setException);

    @NonNull private LocalVariable exception;

    @Override
    public List<BasicBlock> getSuccessors() {
        return Collections.emptyList();
    }

    @Override
    public void updateSuccessors(Function<BasicBlock, BasicBlock> updateFunction) {
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(EXCEPTION);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }
}
