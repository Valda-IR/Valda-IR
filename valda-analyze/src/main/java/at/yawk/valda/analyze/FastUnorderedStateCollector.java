package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.LocalVariable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import lombok.Setter;

/**
 * @author yawkat
 */
public final class FastUnorderedStateCollector<K, V> implements StateCollector<K, V> {
    private final MergeTree<K, Optional<Map<LocalVariable, V>>> tree;
    private final BinaryOperator<Map<LocalVariable, V>> merge;

    @Setter private boolean mergePrevious = false;

    @SuppressWarnings("OptionalIsPresent")
    public FastUnorderedStateCollector(BinaryOperator<Map<LocalVariable, V>> merge) {
        this.tree = new MergeTree<>((a, b) -> {
            if (!a.isPresent()) {
                return b;
            } else if (!b.isPresent()) {
                return a;
            } else {
                return Optional.of(merge.apply(a.get(), b.get()));
            }
        });
        this.merge = merge;
    }

    @Override
    public boolean update(K key, Map<LocalVariable, V> incomingState) {
        Optional<Map<LocalVariable, V>> old = tree.get(key);
        if (mergePrevious && old != null && old.isPresent()) {
            return tree.put(key, Optional.of(merge.apply(old.get(), incomingState)));
        } else {
            return tree.put(key, Optional.of(incomingState));
        }
    }

    @Override
    public void remove(K key) {
        tree.put(key, Optional.empty());
    }

    @Override
    public Set<Map<LocalVariable, V>> getConsolidated() {
        Optional<Map<LocalVariable, V>> opt = tree.getMerged();
        return opt.isPresent() ? Collections.singleton(opt.get()) : Collections.emptySet();
    }

    @Override
    public String toString(Function<K, String> toString) {
        StringBuilder builder = new StringBuilder();
        tree.keySet().stream().sorted(Comparator.comparing(toString)).forEach(sourceMarker -> {
            builder.append("\n\t").append(toString.apply(sourceMarker)).append(" -> ");
            Optional<Map<LocalVariable, V>> optional = tree.get(sourceMarker);
            assert optional != null;
            if (optional.isPresent()) {
                Map<LocalVariable, V> vars = optional.get();
                vars.keySet().stream().sorted(Comparator.comparing(LocalVariable::getName))
                        .forEach(var -> builder.append("\n\t\t")
                                .append(var.getName())
                                .append(": ")
                                .append(vars.get(var)));
            } else {
                builder.append("[unreachable]");
            }
        });
        return builder.toString();
    }
}
