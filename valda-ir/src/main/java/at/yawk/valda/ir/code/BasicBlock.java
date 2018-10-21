package at.yawk.valda.ir.code;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * @author yawkat
 */
public final class BasicBlock {
    private final List<Instruction> instructions = new ArrayList<>();
    @Getter @Setter @Nullable private LocalVariable exceptionVariable = null;
    @Nullable private Try try_ = null;

    private final Set<BlockReference> references = new HashSet<>();

    @Nullable MethodBody body = null;
    /**
     * This could potentially be a single flag. Used for GC of {@link MethodBody#blocks}
     */
    long generation;

    private BasicBlock() {
    }

    public static BasicBlock create() {
        return new BasicBlock();
    }

    void addReference(BlockReference reference) {
        if (body != null && !body.equals(reference.getBody())) {
            throw new IllegalStateException(
                    "Multiple references to same basic block from different methods! This is definitely a programmer " +
                    "error and this exception may have left the block in an inconsistent state.");
        }
        if (!references.add(reference)) {
            throw new IllegalStateException();
        }
    }

    void removeReference(BlockReference reference) {
        if (!references.remove(reference)) {
            throw new IllegalStateException();
        }
    }

    @JsonIgnore
    public Set<BlockReference> getReferences() {
        return Collections.unmodifiableSet(references);
    }

    void onUnreachable(boolean wasClasspathLinked) {
        if (try_ != null) {
            try_.removeEnclosedBlock(this);
        }
        for (Instruction instruction : instructions) {
            if (wasClasspathLinked) {
                instruction.unlinkClasspath();
            }
        }
        if (!instructions.isEmpty()) {
            Instruction last = instructions.get(instructions.size() - 1);
            if (last instanceof TerminatingInstruction) {
                ((TerminatingInstruction) last).unlinkBlocks();
            }
        }
    }

    void onReachable(MethodBody body) {
        assert this.body == null;
        if (!body.blocks.add(this)) {
            throw new IllegalStateException();
        }
        this.body = body;
        if (try_ != null) {
            try_.addEnclosedBlock(this);
        }
        for (Instruction instruction : instructions) {
            if (body.isClasspathLinked()) {
                instruction.linkClasspath();
            }
        }
        if (!instructions.isEmpty()) {
            Instruction last = instructions.get(instructions.size() - 1);
            if (last instanceof TerminatingInstruction) {
                ((TerminatingInstruction) last).linkBlocks();
            }
        }
    }

    void linkClasspath() {
        for (Instruction instruction : instructions) {
            instruction.linkClasspath();
        }
        if (try_ != null) {
            try_.linkClasspath();
        }
    }

    void unlinkClasspath() {
        for (Instruction instruction : instructions) {
            instruction.unlinkClasspath();
        }
        if (try_ != null) {
            try_.unlinkClasspath();
        }
    }

    public void setTry(@Nullable Try try_) {
        if (this.try_ != null && isReachable()) {
            this.try_.removeEnclosedBlock(this);
        }
        this.try_ = try_;
        if (try_ != null && isReachable()) {
            try_.addEnclosedBlock(this);
        }
    }

    @Nullable
    public Try getTry() {
        return try_;
    }

    public void addInstruction(int index, Instruction instruction) {
        if (instruction.block != null) {
            throw new IllegalArgumentException("Instruction already in other block");
        }

        if (instruction instanceof TerminatingInstruction && index < instructions.size()) {
            throw new IllegalArgumentException("Terminating instruction must be last");
        }
        if (index >= instructions.size() && isTerminated()) {
            throw new IllegalArgumentException("Block already has a terminating instruction");
        }

        instruction.block = this;
        instructions.add(index, instruction);
        if (isReachable()) {
            if (instruction instanceof TerminatingInstruction) {
                ((TerminatingInstruction) instruction).linkBlocks();
            }
            assert body != null;
            if (body.isClasspathLinked()) {
                instruction.linkClasspath();
            }
        }
    }

    public void addInstruction(Instruction instruction) {
        addInstruction(instructions.size(), instruction);
    }

    @JsonSetter("instructions")
    public void addInstructions(List<Instruction> instructions) {
        for (Instruction instruction : instructions) {
            addInstruction(instruction);
        }
    }

    public List<Instruction> getInstructions() {
        return Collections.unmodifiableList(instructions);
    }

    public Instruction removeInstruction(int index) {
        Instruction instruction = instructions.remove(index);
        if (isReachable()) {
            if (instruction instanceof TerminatingInstruction) {
                ((TerminatingInstruction) instruction).unlinkBlocks();
            }
            assert body != null;
            if (body.isClasspathLinked()) {
                instruction.unlinkClasspath();
            }
        }
        instruction.block = null;
        return instruction;
    }

    public int indexOf(Instruction instruction) {
        for (int i = 0; i < instructions.size(); i++) {
            //noinspection ObjectEquality
            if (instructions.get(i) == instruction) {
                return i;
            }
        }
        return -1;
    }

    public boolean isTerminated() {
        return !instructions.isEmpty() && instructions.get(instructions.size() - 1) instanceof TerminatingInstruction;
    }

    public TerminatingInstruction getTerminatingInstruction() {
        if (!isTerminated()) { throw new IllegalStateException("Block not terminated"); }
        return (TerminatingInstruction) instructions.get(instructions.size() - 1);
    }

    boolean isReachable() {
        return body != null;
    }

    @NonNull
    public MethodBody getBody() {
        MethodBody body = this.body;
        if (body == null) { throw new IllegalStateException("Block not attached to a MethodBody"); }
        return body;
    }
}
