package at.yawk.valda.ir.dex.compiler;

import java.util.Comparator;
import java.util.List;

/**
 * @author yawkat
 */
class AllocationGroup {
    static final Comparator<AllocationGroup> COMPARATOR =
            Comparator.comparingInt(AllocationGroup::registerBits).thenComparingInt(g -> g.size);

    final List<RegisterAllocationRequest> requests;
    final int size;

    AllocationGroup(List<RegisterAllocationRequest> requests) {
        this.requests = requests;
        this.size = requests.stream().mapToInt(RegisterAllocationRequest::size).sum();
    }

    int registerBits() {
        return requests.stream().mapToInt(req -> req.registerBits).min().orElse(32);
    }
}
