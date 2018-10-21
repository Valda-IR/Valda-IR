package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.LocalVariable;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
public final class OrderedStateCollector<K, V> implements StateCollector<K, V> {
    private final Function<Iterable<Map<LocalVariable, V>>, Set<Map<LocalVariable, V>>> consolidate;
    private final Map<K, Map<LocalVariable, V>> prior = new LinkedHashMap<>();

    @Override
    public boolean update(K key, Map<LocalVariable, V> incomingState) {
        // reinsert to fix LHM order
        Map<LocalVariable, V> old = prior.remove(key);
        prior.put(key, incomingState);
        return !Objects.equals(old, incomingState);
    }

    @Override
    public void remove(K key) {
        prior.remove(key);
    }

    @Override
    public Set<Map<LocalVariable, V>> getConsolidated() {
        return consolidate.apply(prior.values());
    }

    @Override
    public String toString(Function<K, String> toString) {
        StringBuilder builder = new StringBuilder();
        prior.keySet().stream().sorted(Comparator.comparing(toString)).forEach(sourceMarker -> {
            builder.append("\n\t").append(toString.apply(sourceMarker)).append(" -> ");
            Map<LocalVariable, V> vars = prior.get(sourceMarker);
            vars.keySet().stream().sorted(Comparator.comparing(LocalVariable::getName))
                    .forEach(var -> builder.append("\n\t\t")
                            .append(var.getName())
                            .append(": ")
                            .append(vars.get(var)));
        });
        return builder.toString();
    }
}
