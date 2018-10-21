package at.yawk.valda.ir.code;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
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
@EqualsAndHashCode(callSuper = false)
@ToString
public final class Branch extends TerminatingInstruction {
    public static final Slot LHS = Slot.single("lhs", Branch::getLhs, Branch::setLhs);
    public static final Slot RHS = Slot.optional("rhs", Branch::getRhs, Branch::setRhs);

    private final Type type;

    @Getter @NonNull private LocalVariable lhs;
    /**
     * If {@literal null}, compare to zero.
     */
    @Getter @Nullable private LocalVariable rhs;

    @NonNull private BlockReference.Instruction branchTrue;
    @NonNull private BlockReference.Instruction branchFalse;

    @Builder
    Branch(
            @NonNull Type type,
            @NonNull LocalVariable lhs,
            @Nullable LocalVariable rhs,
            @NonNull BasicBlock branchTrue,
            @NonNull BasicBlock branchFalse
    ) {
        this.type = type;
        this.lhs = lhs;
        this.rhs = rhs;
        this.branchTrue = new BlockReference.Instruction(branchTrue, this);
        this.branchFalse = new BlockReference.Instruction(branchFalse, this);
    }

    public BasicBlock getBranchTrue() {
        return branchTrue.getReferencedBlock();
    }

    public void setBranchTrue(BasicBlock branchTrue) {
        BlockReference.Instruction oldBranchTrue = this.branchTrue;
        this.branchTrue = new BlockReference.Instruction(branchTrue, this);
        if (isBlockLinked()) {
            oldBranchTrue.getReferencedBlock().removeReference(oldBranchTrue);
        }
        sweep();
        if (isBlockLinked()) {
            this.branchTrue.getReferencedBlock().addReference(this.branchTrue);
        }
    }

    public BasicBlock getBranchFalse() {
        return branchFalse.getReferencedBlock();
    }

    public void setBranchFalse(BasicBlock branchFalse) {
        BlockReference.Instruction oldBranchFalse = this.branchFalse;
        this.branchFalse = new BlockReference.Instruction(branchFalse, this);
        if (isBlockLinked()) {
            oldBranchFalse.getReferencedBlock().removeReference(oldBranchFalse);
        }
        sweep();
        if (isBlockLinked()) {
            this.branchFalse.getReferencedBlock().addReference(this.branchFalse);
        }
    }

    @Override
    void linkBlocks() {
        this.branchTrue.getReferencedBlock().addReference(this.branchTrue);
        this.branchFalse.getReferencedBlock().addReference(this.branchFalse);
    }

    @Override
    void unlinkBlocks() {
        this.branchTrue.getReferencedBlock().removeReference(this.branchTrue);
        this.branchFalse.getReferencedBlock().removeReference(this.branchFalse);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(LHS, RHS);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }

    @Override
    public List<BasicBlock> getSuccessors() {
        return ImmutableList.of(getBranchTrue(), getBranchFalse());
    }

    @Override
    public void updateSuccessors(Function<BasicBlock, BasicBlock> updateFunction) {
        setBranchTrue(updateFunction.apply(getBranchTrue()));
        setBranchFalse(updateFunction.apply(getBranchFalse()));
    }

    public enum Type {
        EQUAL,
        LESS_THAN,
        GREATER_THAN,
    }

    public static class BranchBuilder {
        public BranchBuilder rhsZero() {
            return rhs(null);
        }
    }
}
