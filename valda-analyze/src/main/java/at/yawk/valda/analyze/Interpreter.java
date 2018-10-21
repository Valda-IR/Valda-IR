package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;

/**
 * @author yawkat
 */
public interface Interpreter<V> {
    default Set<LocalVariable> getInputVariables(BasicBlock block, int indexInBlock) {
        return ImmutableSet.copyOf(block.getInstructions().get(indexInBlock).getInputVariables());
    }

    default Set<LocalVariable> getOutputVariables(BasicBlock block, int indexInBlock) {
        return ImmutableSet.copyOf(block.getInstructions().get(indexInBlock).getOutputVariables());
    }

    @NonNull
    Iterable<ExecutionResult<V>> execute(@NonNull ExecutionContext<V> context);

    <K> StateCollector<K, V> createStateCollector();

    Map<LocalVariable, V> getParameterValues(MethodBody body);

    /**
     * Should a node be reevaluated if an execution path to it disappears?
     *
     * For single-path interpreters, this should be false to prevent reexecution of code that was previously on the
     * execution path but isn't anymore.
     */
    default boolean reevaluateUnreachable() {
        return true;
    }

    default void handleException(Exception e) {
    }
}
