package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.LocalVariable;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * @author yawkat
 */
public interface InstructionNode<V> {
    Instruction getInstruction();

    Collection<Map<LocalVariable, V>> getInput();

    default V getSingleInput(LocalVariable variable) {
        V input = Iterables.getOnlyElement(getInput()).get(variable);
        if (input == null) { throw new NoSuchElementException("No mapping for " + variable + " in " + this); }
        return input;
    }

    default Collection<V> getInput(LocalVariable variable) {
        return getInput().stream()
                .map(m -> m.get(variable))
                .collect(Collectors.toList());
    }

    Collection<InstructionNode<V>> getNextNodes();
}
