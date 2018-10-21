package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.LocalVariable;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * @author yawkat
 */
@Value
@Builder
public final class ExecutionContext<V> {
    private final BasicBlock block;
    private final int indexInBlock;
    private final Map<LocalVariable, V> inputVariables;

    public Instruction getInstruction() {
        return block.getInstructions().get(indexInBlock);
    }

    public V getInput(LocalVariable variable) {
        V v = inputVariables.get(variable);
        if (v == null) {
            throw new IllegalArgumentException("Not an input variable for this instruction: " + variable);
        }
        return v;
    }
}
