package at.yawk.valda.ir;

import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

/**
 * Utility class for ensuring safety of parallel processing in valda. Many operations in valda-IR can be implicitly
 * mutating and not thread-safe. Those operations, such as {@link TypeMirror#method(MemberSignature, TriState)} are
 * guarded using this class. Not all non-thread-safe operations are guarded, only the ones that are not obvious.
 *
 * @author yawkat
 */
@UtilityClass
public final class MutationGuard {
    private static final ThreadLocal<Integer> GUARDED = ThreadLocal.withInitial(() -> 0);

    public static void guard() {
        GUARDED.set(GUARDED.get() + 1);
    }

    public static void unguard() {
        Integer old = GUARDED.get();
        if (old <= 0) { throw new IllegalStateException("Not guarded"); }
        GUARDED.set(old - 1);
    }

    public static void guarded(Runnable r) {
        guard();
        try {
            r.run();
        } finally {
            unguard();
        }
    }

    public static <T> T guarded(Supplier<T> s) {
        guard();
        try {
            return s.get();
        } finally {
            unguard();
        }
    }

    public static void check() {
        if (GUARDED.get() > 0) {
            throw new IllegalStateException(
                    "Mutation guard enabled, but trying to perform guarded non-thread-safe operation.");
        }
    }
}
