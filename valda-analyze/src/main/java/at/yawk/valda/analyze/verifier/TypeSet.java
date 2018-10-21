package at.yawk.valda.analyze.verifier;

import at.yawk.valda.ir.TriState;
import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.Immutable;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@Immutable
public final class TypeSet {
    /**
     * (t1 ∩ t2) ∪ (t3)
     */
    private final Set<FlatTypeSet> intersections;
    @SuppressWarnings("NonFinalFieldInImmutable")
    private int hash; // = 0

    private TypeSet(Set<FlatTypeSet> intersections) {
        this.intersections = intersections;
    }

    public static TypeSet create(Type type) {
        return new TypeSet(Collections.singleton(FlatTypeSet.of(type)));
    }

    private static void addToSet(Set<FlatTypeSet> out, FlatTypeSet union) {
        for (Iterator<FlatTypeSet> iterator = out.iterator(); iterator.hasNext(); ) {
            FlatTypeSet prev = iterator.next();
            if (union.containsAll(prev)) {
                // prev is less specific. no need to add the new union
                return;
            }
            if (prev.containsAll(union)) {
                // prev is more specific, we can remove it.
                iterator.remove();
            }
        }

        out.add(union);
    }

    public static TypeSet intersect(TypeSet a, TypeSet b) {
        Set<FlatTypeSet> out = new HashSet<>();
        for (FlatTypeSet intersectionA : a.intersections) {
            for (FlatTypeSet intersectionB : b.intersections) {
                addToSet(out, FlatTypeSet.union(intersectionA, intersectionB));
            }
        }
        return new TypeSet(out);
    }

    public static TypeSet union(TypeSet a, TypeSet b) {
        // union must be eager
        Set<FlatTypeSet> out = new HashSet<>();
        for (FlatTypeSet intersectionA : a.intersections) {
            addToSet(out, intersectionA);
        }
        for (FlatTypeSet intersectionB : b.intersections) {
            addToSet(out, intersectionB);
        }

        return new TypeSet(out);
    }

    public boolean isSingleType() {
        return intersections.size() == 1 && intersections.iterator().next().size == 1;
    }

    public Type getSingleType() {
        if (!isSingleType()) { throw new IllegalStateException(); }
        return intersections.iterator().next().iterator().next();
    }

    public TriState matches(Predicate<Type> predicate) {
        return matchesTri(t -> TriState.valueOf(predicate.test(t)));
    }

    public TriState matchesTri(Function<Type, TriState> seed) {
        return reduce(seed, TriState::or, TriState::and);
    }

    public <T> T reduce(Function<Type, T> seed, BinaryOperator<T> intersect, BinaryOperator<T> union) {
        // this is just a nested reduce. Streams could do this nicely, but don't support nulls.y
        T outer = null;
        boolean firstOuter = true;
        for (Iterable<Type> intersection : intersections) {
            T inner = null;
            boolean firstInner = true;
            for (Type type : intersection) {
                T s = seed.apply(type);
                if (firstInner) {
                    inner = s;
                    firstInner = false;
                } else {
                    inner = intersect.apply(inner, s);
                }
            }
            if (firstOuter) {
                outer = inner;
                firstOuter = false;
            } else {
                outer = union.apply(outer, inner);
            }
        }
        return outer;
    }

    /**
     * @see #mapNullable(Function)
     */
    @NonNull
    public TypeSet map(Function<Type, @NotNull Type> function) {
        return mapNullable(function);
    }

    /**
     * Apply a map operation to the types in this set.
     *
     * Should the given function return {@code null} for a type, that type is discarded. For example, to normalize a
     * TypeSet, one might map all {@code java.lang.Object} types to {@code null} and will get a "cleaner" typeset back.
     *
     * {@code null} is returned when the resulting TypeSet is equivalent to {@code java.lang.Object} (assuming
     * reference types).
     */
    @Nullable
    public TypeSet mapNullable(Function<Type, @Nullable Type> function) {
        Set<FlatTypeSet> out = new HashSet<>();
        for (Iterable<Type> intersection : intersections) {
            FlatTypeSet newIntersection = new FlatTypeSet();
            for (Type type : intersection) {
                Type mapped = function.apply(type);
                if (mapped != null) {
                    newIntersection.add(mapped);
                }
            }
            if (newIntersection.isEmpty()) {
                return null;
            }
            out.add(newIntersection);
        }
        return new TypeSet(out);
    }

    @Override
    public String toString() {
        return intersections.stream()
                .map(intersection -> intersection.stream()
                        .map(Type::getDescriptor)
                        .collect(Collectors.joining(" ∩ ", "(", ")")))
                .collect(Collectors.joining(" ∪ ", "[", "]"));
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = intersections.hashCode();
            if (h == 0) { h = 1; }
            hash = h;
        }
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TypeSet)) { return false; }
        if (this == obj) { return true; }
        if (hashCode() != obj.hashCode()) { return false; }
        return intersections.equals(((TypeSet) obj).intersections);
    }

    @VisibleForTesting
    static class FlatTypeSet implements Iterable<Type> {
        private static final Type[] EMPTY_TYPES = new Type[0];
        private static final String[] EMPTY_DESCRIPTORS = new String[0];

        private Type[] types = EMPTY_TYPES;
        private String[] descriptors = EMPTY_DESCRIPTORS;
        int size;

        public static FlatTypeSet of(Type type) {
            FlatTypeSet set = new FlatTypeSet();
            set.types = new Type[]{ type };
            set.descriptors = new String[]{ type.getDescriptor() };
            set.size = 1;
            return set;
        }

        public static FlatTypeSet union(FlatTypeSet a, FlatTypeSet b) {
            return join(a, b, true);
        }

        public static FlatTypeSet intersect(FlatTypeSet a, FlatTypeSet b) {
            return join(a, b, false);
        }

        private static FlatTypeSet join(FlatTypeSet a, FlatTypeSet b, boolean union) {
            int aI = 0, bI = 0;
            FlatTypeSet out = new FlatTypeSet();
            while (true) {
                int cmp;
                if (aI < a.size && bI < b.size) {
                    cmp = a.descriptors[aI].compareTo(b.descriptors[bI]);
                } else if (aI < a.size) {
                    cmp = -1;
                } else if (bI < b.size) {
                    cmp = 1;
                } else {
                    break;
                }
                if (cmp == 0) {
                    out.addEnd(a.types[aI], a.descriptors[aI]);
                    aI++;
                    bI++;
                } else if (cmp < 0) {
                    if (union) { out.addEnd(a.types[aI], a.descriptors[aI]); }
                    aI++;
                } else {
                    if (union) { out.addEnd(b.types[bI], b.descriptors[bI]); }
                    bI++;
                }
            }
            return out;
        }

        public boolean add(Type type) {
            String desc = type.getDescriptor();
            int i = Arrays.binarySearch(descriptors, 0, size, desc);
            if (i >= 0) {
                return false;
            } else {
                i = ~i;
                growIfNecessary();
                System.arraycopy(types, i, types, i + 1, size - i);
                System.arraycopy(descriptors, i, descriptors, i + 1, size - i);
                types[i] = type;
                descriptors[i] = desc;
                size++;
                return true;
            }
        }

        private void addEnd(Type type, String desc) {
            assert size == 0 || descriptors[size - 1].compareTo(desc) < 0;
            assert type.getDescriptor().equals(desc);
            growIfNecessary();
            types[size] = type;
            descriptors[size] = desc;
            size++;
        }

        private void growIfNecessary() {
            if (types.length <= size) {
                int newCapacity = types.length * 2;
                if (newCapacity == 0) {
                    newCapacity = 12;
                }
                types = Arrays.copyOf(types, newCapacity);
                descriptors = Arrays.copyOf(descriptors, newCapacity);
            }
        }

        @NonNull
        @Override
        public Iterator<Type> iterator() {
            return new Iterator<Type>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    return i < size;
                }

                @Override
                public Type next() {
                    if (!hasNext()) { throw new NoSuchElementException(); }
                    return types[i++];
                }
            };
        }

        public Stream<Type> stream() {
            return Arrays.stream(types, 0, size);
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public boolean containsAll(FlatTypeSet other) {
            if (other.size > size) { return false; }
            int i = 0, j = 0;
            while (i < size && j < other.size) {
                int cmp = descriptors[i].compareTo(other.descriptors[j]);
                if (cmp == 0) {
                    i++;
                    j++;
                } else if (cmp < 0) {
                    i++;
                } else {
                    return false;
                }
            }
            return j >= other.size;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof FlatTypeSet)) { return false; }
            if (((FlatTypeSet) obj).size != size) { return false; }
            for (int i = 0; i < size; i++) {
                if (!((FlatTypeSet) obj).descriptors[i].equals(descriptors[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hc = 0;
            for (int i = 0; i < size; i++) {
                hc = hc * 31 + descriptors[i].hashCode();
            }
            return hc;
        }
    }
}
