package at.yawk.valda.ir.dex.compiler;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;

/**
 * @author yawkat
 */
final class CodeOutput {
    private final int expectedInstructionsSize;

    private final List<ImmutableInstruction> instructions;
    private int instructionsEnd;
    private final List<ImmutableInstruction> footer;
    private int footerEnd;

    CodeOutput(int expectedInstructionsSize) {
        this.expectedInstructionsSize = expectedInstructionsSize;
        instructions = new ArrayList<>();
        instructionsEnd = 0;
        footer = new ArrayList<>();
        footerEnd = expectedInstructionsSize;
    }

    void expectOffset(int offset) {
        if (instructionsEnd != offset) {
            throw new CompileException("Unexpected offset");
        }
    }

    void pushInsn(Instruction insn) {
        instructions.add(ImmutableInstruction.of(insn));
        instructionsEnd += insn.getCodeUnits();
    }

    /**
     * Push the given <i>footer</i> instruction, such as an array payload.
     *
     * @return The offset from the <i>current</i> instruction (as in, the position of the next instruction given to
     * {@link #pushInsn(Instruction)}) to the placed instruction.
     */
    int pushFooter(Instruction payload) {
        while ((footerEnd % 4) != 0) {
            footer.add(new ImmutableInstruction10x(Opcode.NOP));
            footerEnd++;
        }
        int start = footerEnd;
        footer.add(ImmutableInstruction.of(payload));
        footerEnd += payload.getCodeUnits();
        return start - instructionsEnd;
    }

    ImmutableList<ImmutableInstruction> finish() {
        if (instructionsEnd != expectedInstructionsSize) {
            throw new CompileException(
                    "Instruction end was " + instructionsEnd + " but expected " + expectedInstructionsSize);
        }
        return ImmutableList.<ImmutableInstruction>builder().addAll(instructions).addAll(footer).build();
    }
}
