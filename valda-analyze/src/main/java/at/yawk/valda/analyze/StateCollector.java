package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.LocalVariable;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author yawkat
 */
public interface StateCollector<K, V> {
    boolean update(K key, Map<LocalVariable, V> incomingState);

    void remove(K key);

    Set<Map<LocalVariable, V>> getConsolidated();

    String toString(Function<K, String> toString);
}
