package at.yawk.valda.ir.dex.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * This class takes a collection of {@link InsnNode}s and orders it so all their
 * {@link ConstrainedNodeDependency ConstrainedNodeDependencies} are fulfilled.
 *
 * As a side effect, it determines {@link InsnNode#needsGoto} and provides a pessimistic {@link InsnNode#offset}
 * (meaning the instructions and offset distances may be smaller in the final result, but never larger).
 *
 * @author yawkat
 */
@Slf4j
final class InstructionOrderer {
    private static final boolean CHECK_INVARIANTS = false; // also assertions need to be on

    private static final int MAX_GOTO_SIZE = 3;

    private static final Comparator<ConstrainedNodeDependency> DEPENDENCY_COMPARATOR =
            Comparator.<ConstrainedNodeDependency>comparingInt(d -> d.latestOffset)
                    .thenComparingInt(d -> d.width);

    private final Deque<InsnNode<?>> nodeQueue;
    private final List<ConstrainedNodeDependency> currentDependencies = new ArrayList<>();
    private int offset = 0;

    final List<InsnNode<?>> nodesOrdered;

    InstructionOrderer(List<InsnNode<?>> nodes) {
        nodeQueue = new ArrayDeque<>(nodes);
        nodesOrdered = new ArrayList<>(nodes.size());
    }

    private boolean invariants() {
        if (!CHECK_INVARIANTS) { return true; }

        for (ConstrainedNodeDependency dependency : currentDependencies) {
            assert dependency.latestOffset >= offset;
            assert !nodesOrdered.contains(dependency.latestNode);
        }
        assert currentDependencies.stream()
                .sorted(DEPENDENCY_COMPARATOR)
                .collect(Collectors.toList())
                .equals(currentDependencies);

        return true; // so we can use this in an assert
    }

    void run() {
        int violationBacktrackDepth = 0;
        while (true) {
            assert invariants();

            {
                InsnNode<?> node = nodeQueue.pollFirst();
                if (node == null) { break; }
                if (node.offset != InsnNode.UNKNOWN_OFFSET) { continue; }

                add(node);
            }

            if (!currentDependencies.isEmpty() && currentDependencies.get(0).latestOffset < offset + MAX_GOTO_SIZE) {
                // bail! this dependency needs to be satisfied before we can insert this node
                int maxBacktrackDepth = nodesOrdered.size() - 1;
                if (maxBacktrackDepth == violationBacktrackDepth) { throw new AssertionError(); }
                violationBacktrackDepth++;
                for (ConstrainedNodeDependency dependency : currentDependencies) {
                    if (dependency.latestOffset < offset + MAX_GOTO_SIZE) {
                        violationBacktrackDepth++;
                    } else {
                        break;
                    }
                }
                violationBacktrackDepth = Math.min(violationBacktrackDepth, maxBacktrackDepth);

                log.trace("backtrack {} size={}", violationBacktrackDepth, nodesOrdered.size());
                for (int i = 0; i < violationBacktrackDepth; i++) {
                    backtrack();
                }
                InsnNode<?> priority = currentDependencies.get(0).latestNode;
                nodeQueue.remove(priority);
                nodeQueue.addFirst(priority);
            } else {
                if (violationBacktrackDepth > 0) { violationBacktrackDepth--; }
            }
        }
        assert invariants();
    }

    private void add(InsnNode<?> node) {
        // determine previous.needsGoto and and update offset accordingly
        if (!nodesOrdered.isEmpty()) {
            InsnNode<?> previous = nodesOrdered.get(nodesOrdered.size() - 1);
            offset -= predictedSize(previous);
            previous.needsGoto = previous.continueTo != null && !previous.continueTo.equals(node);
            offset += predictedSize(previous);
        }

        // assume we need goto for now, it'll be fixed in the next iteration if this is not the case.
        // this assumption allows us to avoid special-casing the last instruction of the method
        node.needsGoto = node.continueTo != null;
        node.offset = offset;
        nodesOrdered.add(node);

        // check dependencies
        for (ConstrainedNodeDependency dependency : node.dependencies) {
            int from = dependency.from.offset;
            int to = dependency.to.offset;
            // at least one of these should have been set up above as node.offset
            if (from == InsnNode.UNKNOWN_OFFSET && to == InsnNode.UNKNOWN_OFFSET) { throw new AssertionError(); }
            if (from != InsnNode.UNKNOWN_OFFSET && to != InsnNode.UNKNOWN_OFFSET) {
                // satisfied
                removeDependency(dependency);
                if (dependency.latestOffset < offset) { throw new AssertionError(); }
                continue;
            }

            assert dependency.width < 32;
            if (from != InsnNode.UNKNOWN_OFFSET) {
                dependency.latestOffset = from + (1 << (dependency.width - 1));
                dependency.latestNode = dependency.to;
            } else {
                dependency.latestOffset = to + (1 << (dependency.width - 1))
                                          // since the jump could happen anywhere in the from statement, be
                                          // pessimistic about it and assume it happens just before the end.
                                          - (dependency.from.getSize() - 1);
                dependency.latestNode = dependency.from;
            }
            addDependency(dependency);
        }

        offset += predictedSize(node);
    }

    private void backtrack() {
        if (nodesOrdered.size() <= 1) { throw new IllegalStateException("Cannot backtrack over first node"); }

        InsnNode<?> revert = nodesOrdered.remove(nodesOrdered.size() - 1);
        revert.offset = InsnNode.UNKNOWN_OFFSET;
        offset -= predictedSize(revert);
        for (ConstrainedNodeDependency dependency : revert.dependencies) {
            if (dependency.latestNode.equals(revert)) {
                addDependency(dependency);
            } else {
                removeDependency(dependency);
            }
        }
        nodeQueue.addLast(revert);
    }

    private void removeDependency(ConstrainedNodeDependency dependency) {
        // removed deps are *usually* at the end of the list somewhere. A binary search would also be nice but our
        // DEPENDENCY_COMPARATOR can have multiple elements with the same order
        for (int i = currentDependencies.size() - 1; i >= 0; i--) {
            if (currentDependencies.get(i).equals(dependency)) {
                currentDependencies.remove(i);
                return;
            }
        }
        throw new AssertionError();
    }

    private void addDependency(ConstrainedNodeDependency dependency) {
        // fast path - should be hit almost always
        if (currentDependencies.isEmpty() ||
            currentDependencies.get(currentDependencies.size() - 1).latestOffset <= dependency.latestOffset) {

            currentDependencies.add(dependency);
            return;
        }

        int i = Collections.binarySearch(currentDependencies, dependency, DEPENDENCY_COMPARATOR);
        currentDependencies.add(i < 0 ? ~i : i, dependency);
    }

    private static int predictedSize(InsnNode<?> node) {
        return node.getSize() + (node.needsGoto ? MAX_GOTO_SIZE : 0);
    }
}
