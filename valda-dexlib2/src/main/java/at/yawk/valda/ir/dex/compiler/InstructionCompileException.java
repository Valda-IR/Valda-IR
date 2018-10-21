package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.code.Instruction;

/**
 * @author yawkat
 */
class InstructionCompileException extends CompileException {
    InstructionCompileException(Instruction insn, String message) {
        super(message + ": " + insn);
    }

    public InstructionCompileException(Instruction instruction, Exception e) {
        super(instruction.toString(), e);
    }
}
