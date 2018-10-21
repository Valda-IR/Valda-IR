package at.yawk.valda.ir.code;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/**
 * @author yawkat
 */
@EqualsAndHashCode(callSuper = false)
@ToString
public final class GoTo extends TerminatingInstruction {
    @NonNull private BlockReference.Instruction target;

    private GoTo(BasicBlock target) {
        this.target = new BlockReference.Instruction(target, this);
    }

    public static GoTo create(BasicBlock target) {
        return new GoTo(target);
    }

    public void setTarget(@NonNull BasicBlock target) {
        BlockReference.Instruction oldTarget = this.target;
        this.target = new BlockReference.Instruction(target, this);
        if (isBlockLinked()) {
            oldTarget.getReferencedBlock().removeReference(oldTarget);
        }
        sweep();
        if (isBlockLinked()) {
            target.addReference(this.target);
        }
    }

    public BasicBlock getTarget() {
        return target.getReferencedBlock();
    }

    @Override
    void linkBlocks() {
        target.getReferencedBlock().addReference(target);
    }

    @Override
    void unlinkBlocks() {
        target.getReferencedBlock().removeReference(target);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of();
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }

    @Override
    public List<BasicBlock> getSuccessors() {
        return Collections.singletonList(getTarget());
    }

    @Override
    public void updateSuccessors(Function<BasicBlock, BasicBlock> updateFunction) {
        setTarget(updateFunction.apply(getTarget()));
    }
}
