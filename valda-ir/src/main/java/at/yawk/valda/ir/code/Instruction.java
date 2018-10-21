package at.yawk.valda.ir.code;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * @author yawkat
 */
public abstract class Instruction {
    @Nullable BasicBlock block = null;

    Instruction() {
    }

    public boolean hasBlock() {
        return block != null;
    }

    @JsonIgnore
    public BasicBlock getBlock() throws IllegalStateException {
        if (block == null) { throw new IllegalStateException(); }
        return block;
    }

    boolean isBlockLinked() {
        return block != null && block.isReachable();
    }

    void sweep() {
        if (block != null && block.body != null) {
            block.body.sweep();
        }
    }

    boolean isClasspathLinked() {
        //noinspection ConstantConditions
        return isBlockLinked() && block.body.isClasspathLinked();
    }

    void linkClasspath() {
    }

    void unlinkClasspath() {
    }

    public final void addAfter(Instruction instruction) {
        if (block == null) { throw new IllegalStateException("Instruction not attached to block"); }
        block.addInstruction(block.indexOf(this) + 1, instruction);
    }

    public final void addBefore(Instruction instruction) {
        if (block == null) { throw new IllegalStateException("Instruction not attached to block"); }
        block.addInstruction(block.indexOf(this), instruction);
    }

    @JsonIgnore
    public Collection<LocalVariable> getInputVariables() {
        return getInputSlots().stream()
                .flatMap(sl -> sl.getVariables(this).stream())
                .collect(Collectors.toList());
    }

    @JsonIgnore
    public Collection<LocalVariable> getOutputVariables() {
        return getOutputSlots().stream()
                .flatMap(sl -> sl.getVariables(this).stream())
                .collect(Collectors.toList());
    }

    @JsonIgnore
    public abstract Collection<Slot> getInputSlots();

    @JsonIgnore
    public abstract Collection<Slot> getOutputSlots();
}
