package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.LocalVariable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;

/**
 * @author yawkat
 */
public class SinglePassIntInterpreter extends IntInterpreter {
    @Override
    public <K> StateCollector<K, ImmutableIntSet> createStateCollector() {
        return new StateCollector<K, ImmutableIntSet>() {
            Map<LocalVariable, ImmutableIntSet> state;

            @Override
            public boolean update(K key, Map<LocalVariable, ImmutableIntSet> incomingState) {
                if (incomingState.equals(state)) {
                    return false;
                } else {
                    state = incomingState;
                    return true;
                }
            }

            @Override
            public void remove(K key) {
                state = null;
            }

            @Override
            public Set<Map<LocalVariable, ImmutableIntSet>> getConsolidated() {
                return Collections.singleton(state);
            }

            @Override
            public String toString(Function<K, String> toString) {
                return Objects.toString(state);
            }
        };
    }
}
