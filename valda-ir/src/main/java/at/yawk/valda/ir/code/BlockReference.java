package at.yawk.valda.ir.code;

import lombok.Getter;

/**
 * @author yawkat
 */
public abstract class BlockReference {
    @Getter private final BasicBlock referencedBlock;

    BlockReference(BasicBlock referencedBlock) {
        this.referencedBlock = referencedBlock;
    }

    abstract MethodBody getBody();

    @Getter
    public static class Instruction extends BlockReference {
        private final TerminatingInstruction instruction;

        Instruction(BasicBlock referencedBlock, TerminatingInstruction instruction) {
            super(referencedBlock);
            this.instruction = instruction;
        }

        @Override
        MethodBody getBody() {
            if (instruction.block == null) { throw new IllegalStateException(); }
            if (instruction.block.body == null) { throw new IllegalStateException(); }
            return instruction.block.body;
        }
    }

    @Getter
    public static final class SwitchBranch extends Instruction {
        private final int branch;

        SwitchBranch(BasicBlock referencedBlock, Switch instruction, int branch) {
            super(referencedBlock, instruction);
            this.branch = branch;
        }

        @Override
        public Switch getInstruction() {
            return (Switch) super.getInstruction();
        }
    }

    public static final class CatchHandler extends BlockReference {
        private final Try.Catch _catch;

        CatchHandler(BasicBlock referencedBlock, Try.Catch _catch) {
            super(referencedBlock);
            this._catch = _catch;
        }

        public Try.Catch getCatch() {
            return _catch;
        }

        @Override
        MethodBody getBody() {
            MethodBody body = null;
            for (BasicBlock enclosed : _catch.getTry().getEnclosedBlocks()) {
                if (enclosed.body == null) { throw new IllegalStateException(); }
                if (body != null && !body.equals(enclosed.body)) {
                    throw new IllegalStateException("References to same catch handler from multiple methods");
                }
                body = enclosed.body;
            }
            if (body == null) { throw new IllegalStateException(); }
            return body;
        }
    }

    @Getter
    public static final class EntryPoint extends BlockReference {
        EntryPoint(BasicBlock referencedBlock, MethodBody body) {
            super(referencedBlock);
            this.body = body;
        }

        private final MethodBody body;
    }
}
