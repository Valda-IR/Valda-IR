package at.yawk.valda.ir.code;

import java.util.List;
import java.util.function.Function;

/**
 * @author yawkat
 */
public abstract class TerminatingInstruction extends Instruction {
    TerminatingInstruction() {
    }

    public abstract List<BasicBlock> getSuccessors();

    public abstract void updateSuccessors(Function<BasicBlock, BasicBlock> updateFunction);

    void linkBlocks() {
    }

    void unlinkBlocks() {
    }
}
