package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.Try;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * @author yawkat
 */
public abstract class ExecutionResult<V> {
    private ExecutionResult() {
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Throw<V> extends ExecutionResult<V> {
        @NonNull private final Try.Catch destination;
        /**
         * The exception value. May only be null if the {@link BasicBlock#exceptionVariable} is null for the
         * destination.
         */
        @Nullable private final V exception;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class ThrowMethod<V> extends ExecutionResult<V> {
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    @Builder
    public static final class Continue<V> extends ExecutionResult<V> {
        private static final Continue<?> NO_CHANGE = new Continue<>(Collections.emptyMap());

        @Singular private final Map<LocalVariable, V> outputVariables;

        @SuppressWarnings("unchecked")
        public static <V> Continue<V> noChange() {
            return (Continue<V>) NO_CHANGE;
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Branch<V> extends ExecutionResult<V> {
        private final BasicBlock target;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Return<V> extends ExecutionResult<V> {
    }
}
