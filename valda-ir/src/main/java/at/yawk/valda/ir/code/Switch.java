package at.yawk.valda.ir.code;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

/**
 * @author yawkat
 */
@ToString
@EqualsAndHashCode(callSuper = false)
public final class Switch extends TerminatingInstruction {
    public static final Slot OPERAND = Slot.single("operand", Switch::getOperand, Switch::setOperand);

    @NonNull @Getter @Setter private LocalVariable operand;
    @NonNull private final MutableIntObjectMap<BlockReference.SwitchBranch> branches = IntObjectMaps.mutable.empty();
    @NonNull private BlockReference.Instruction defaultBranch;

    private Switch(@NonNull LocalVariable operand, BasicBlock defaultBranch) {
        this.operand = operand;
        this.defaultBranch = new BlockReference.Instruction(defaultBranch, this);
    }

    public static Switch create(LocalVariable operand, BasicBlock defaultBranch) {
        return new Switch(operand, defaultBranch);
    }

    public IntObjectMap<BasicBlock> getBranches() {
        MutableIntObjectMap<BasicBlock> mapped = IntObjectMaps.mutable.empty();
        branches.forEachKeyValue((k, v) -> mapped.put(k, v.getReferencedBlock()));
        return mapped;
    }

    public void removeBranch(int i) {
        BlockReference.SwitchBranch ref = branches.remove(i);
        if (isBlockLinked()) {
            ref.getReferencedBlock().removeReference(ref);
        }
    }

    public void addBranch(int i, BasicBlock target) {
        BlockReference.SwitchBranch ref = new BlockReference.SwitchBranch(target, this, i);
        BlockReference.SwitchBranch old = branches.put(i, ref);
        if (isBlockLinked()) {
            if (old != null) {
                old.getReferencedBlock().removeReference(old);
            }
        }
        sweep();
        if (isBlockLinked()) {
            target.addReference(ref);
        }
    }

    public void addBranches(IntObjectMap<BasicBlock> branches) {
        branches.forEachKeyValue(this::addBranch);
    }

    public BasicBlock getDefaultBranch() {
        return defaultBranch.getReferencedBlock();
    }

    public void setDefaultBranch(BasicBlock defaultBranch) {
        BlockReference.Instruction oldDefault = this.defaultBranch;
        this.defaultBranch = new BlockReference.Instruction(defaultBranch, this);
        if (isBlockLinked()) {
            oldDefault.getReferencedBlock().removeReference(oldDefault);
        }
        sweep();
        if (isBlockLinked()) {
            defaultBranch.addReference(this.defaultBranch);
        }
    }

    @Override
    public List<BasicBlock> getSuccessors() {
        return Stream.concat(branches.values().stream(), Stream.of(defaultBranch))
                .map(BlockReference::getReferencedBlock)
                .collect(Collectors.toList());
    }

    @Override
    public void updateSuccessors(Function<BasicBlock, BasicBlock> updateFunction) {
        branches.forEachKeyValue((key, value) -> addBranch(key, updateFunction.apply(value.getReferencedBlock())));
    }

    @Override
    void linkBlocks() {
        defaultBranch.getReferencedBlock().addReference(defaultBranch);
        branches.values().forEach(b -> b.getReferencedBlock().addReference(b));
    }

    @Override
    void unlinkBlocks() {
        defaultBranch.getReferencedBlock().removeReference(defaultBranch);
        branches.values().forEach(b -> b.getReferencedBlock().removeReference(b));
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(OPERAND);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }
}
