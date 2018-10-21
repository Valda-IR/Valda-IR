package at.yawk.valda.analyze;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BinaryOperator;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
@NotThreadSafe
public final class MergeTree<K, V> {
    private final BinaryOperator<V> merge;

    private final MutableObjectIntMap<K> keys = ObjectIntMaps.mutable.empty();
    private final List<V> values = new ArrayList<>();
    private final List<List<V>> tree = new ArrayList<>();

    { tree.add(values); }

    public boolean put(K key, @NonNull V value) {
        int i = keys.getIfAbsent(key, -1);
        if (i == -1) {
            keys.put(key, i = values.size());
            values.add(value);
            int j = 1;
            while (i != 0) {
                i /= 2;
                if (j >= tree.size()) { tree.add(new ArrayList<>()); }
                List<V> layer = tree.get(j++);
                if (layer.size() <= i) {
                    layer.add(null);
                } else {
                    while (true) {
                        if (layer.set(i, null) == null) {
                            // already null, don't need to go further up
                            break;
                        }
                        if (j >= tree.size()) { break; }
                        layer = tree.get(j++);
                        i /= 2;
                    }
                    break;
                }
            }
            return true;
        } else {
            V old = values.set(i, value);
            if (value.equals(old)) { return false; }
            for (int j = 1; j < tree.size(); j++) {
                i /= 2;
                List<V> layer = tree.get(j);
                if (layer.set(i, null) == null) {
                    // already null, don't need to go further up
                    break;
                }
            }
            return true;
        }
    }

    @NonNull
    public V getMerged() {
        V result = get(tree.size() - 1, 0);
        if (result == null) { throw new NoSuchElementException("Tree empty"); }
        return result;
    }

    @Nullable
    public V get(K key) {
        int i = keys.getIfAbsent(key, -1);
        return i == -1 ? null : values.get(i);
    }

    public Set<K> keySet() {
        return keys.keySet();
    }

    private V get(int layer, int i) {
        List<V> l = tree.get(layer);
        if (l.size() <= i) { return null; }
        V memoized = l.get(i);
        if (memoized == null) {
            V lhs = get(layer - 1, i * 2);
            V rhs = get(layer - 1, i * 2 + 1);
            memoized = rhs == null ? lhs : merge.apply(lhs, rhs);
            l.set(i, memoized);
        }
        return memoized;
    }
}
