package at.yawk.valda.analyze;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.Try;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
@NotThreadSafe
@Slf4j
public final class Analyzer<V> {
    private final Interpreter<V> interpreter;

    private final Map<BasicBlock, List<Node>> nodes = new HashMap<>();
    private final Queue<Node> queue = new ArrayDeque<>();

    public final void interpret(MethodBody body) {
        Map<LocalVariable, V> parameters = interpreter.getParameterValues(body);
        if (!ImmutableSet.copyOf(body.getParameters()).equals(parameters.keySet())) {
            throw new IllegalArgumentException(
                    "Parameter mismatch: Expected " + body.getParameters() + " but got " + parameters.keySet());
        }
        Node entryPoint = getNode(body.getEntryPoint(), 0);
        SourceMarker parameterMarker = new SourceMarker("param");
        entryPoint.stateCollector.update(parameterMarker, parameters);
        entryPoint.markDirty();

        int generation = 0;
        while (true) {
            if (log.isTraceEnabled()) {
                log.trace("Generation {}: queue [{}]",
                          generation++,
                          queue.stream().map(n -> Integer.toString(n.id)).collect(Collectors.joining(", ")));
            }
            Node n = queue.poll();
            if (n == null) { break; }
            n.dirty = false;
            try {
                n.run();
            } catch (Exception e) {
                interpreter.handleException(e);
                //noinspection ObjectToString
                throw new AnalyzerException(
                        "Failed to execute instruction " + n.instruction + " (at " + n.block + "#" + n.index + ")", e);
            }
        }
    }

    private Node getNode(BasicBlock block, int index) {
        List<Node> l = getNodes0(block);
        Node node = l.get(index);
        if (node == null) {
            l.set(index, node = new Node(block, index));
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private List<Node> getNodes0(BasicBlock block) {
        return this.nodes.computeIfAbsent(block, b -> Arrays.asList(new Analyzer.Node[b.getInstructions().size()]));
    }

    /**
     * Get the instruction nodes for a given block. May contain null entries if a node has never been visited.
     */
    public List<InstructionNode<V>> getNodes(BasicBlock block) {
        return Collections.unmodifiableList(getNodes0(block));
    }

    private int nextNodeId = 0;

    private final class Node implements InstructionNode<V> {
        private final int id = nextNodeId++;
        private final BasicBlock block;
        private final int index;
        @Getter private final Instruction instruction;
        private final Set<LocalVariable> inputVariables;
        private final Set<LocalVariable> outputVariables;

        Node(BasicBlock block, int index) {
            this.block = block;
            this.index = index;

            instruction = block.getInstructions().get(index);
            inputVariables = interpreter.getInputVariables(block, index);
            outputVariables = interpreter.getOutputVariables(block, index);
        }

        private final StateCollector<SourceMarker, V> stateCollector = interpreter.createStateCollector();

        private Set<Map<LocalVariable, V>> consolidate;

        private final SourceMarker normalMarker = new SourceMarker(Integer.toString(id));
        private final SourceMarker throwMarker = new SourceMarker(id + "/throw");

        private Set<ContinueTarget> previousTargets = new HashSet<>();

        private boolean dirty = false;

        void run() {
            consolidate = stateCollector.getConsolidated();
            Set<ContinueTarget> newTargets = new HashSet<>();

            // if consolidate is empty, this instruction has become unreachable, and we only need to clean up all
            // successors.
            if (!consolidate.isEmpty()) {
                for (Map<LocalVariable, V> priorState : consolidate) {
                    Map<LocalVariable, V> filteredInput = Maps.filterKeys(priorState, this.inputVariables::contains);
                    if (!filteredInput.keySet().equals(inputVariables)) {
                        throw new IllegalStateException(
                                "Missing input for instruction " + instruction + ": expected " + inputVariables +
                                " but only got " + filteredInput);
                    }
                    ExecutionContext<V> context = ExecutionContext.<V>builder()
                            .block(block).indexInBlock(index)
                            .inputVariables(filteredInput)
                            .build();
                    Iterable<ExecutionResult<V>> results = interpreter.execute(context);
                    for (ExecutionResult<V> result : results) {
                        ContinueTarget target = handleResult(priorState, result);
                        if (target != null) {
                            newTargets.add(target);
                        }
                    }
                }
            }

            previousTargets.removeAll(newTargets);
            for (ContinueTarget nowDead : previousTargets) {
                nowDead.destination.stateCollector.remove(nowDead.marker);
                if (interpreter.reevaluateUnreachable()) {
                    log.trace("{} is now unreachable from {}, marking dirty", nowDead, this);
                    nowDead.destination.markDirty();
                }
            }
            previousTargets = newTargets;
        }

        void markDirty() {
            if (!dirty) {
                dirty = true;
                queue.add(this);
            }
        }

        @Nullable
        private ContinueTarget handleResult(Map<LocalVariable, V> priorState, ExecutionResult<V> result) {
            if (result instanceof ExecutionResult.Branch) {
                BasicBlock targetBlock = ((ExecutionResult.Branch<V>) result).getTarget();
                ContinueTarget target = new ContinueTarget(getNode(targetBlock, 0), normalMarker);
                goTo(target, priorState);
                return target;
            } else if (result instanceof ExecutionResult.Continue) {
                Map<LocalVariable, V> output = ((ExecutionResult.Continue<V>) result).getOutputVariables();
                if (!output.keySet().equals(this.outputVariables)) {
                    throw new AnalyzerException(
                            "Output variable mismatch: expected " + this.outputVariables + " but got " +
                            output.keySet());
                }
                Map<LocalVariable, V> newState = ImmutableMap.<LocalVariable, V>builder()
                        .putAll(Maps.filterKeys(priorState, k -> !this.outputVariables.contains(k)))
                        .putAll(output)
                        .build();
                ContinueTarget target = new ContinueTarget(getNode(block, index + 1), normalMarker);
                goTo(target, newState);
                return target;
            } else if (result instanceof ExecutionResult.Return || result instanceof ExecutionResult.ThrowMethod) {
                // return from method
                return null;
            } else if (result instanceof ExecutionResult.Throw) {
                Try.Catch destination = ((ExecutionResult.Throw<V>) result).getDestination();
                ContinueTarget target = new ContinueTarget(getNode(destination.getHandler(), 0), throwMarker);
                Map<LocalVariable, V> newState;
                LocalVariable exceptionVariable = destination.getHandler().getExceptionVariable();
                if (exceptionVariable == null) {
                    newState = priorState;
                } else {
                    V exception = ((ExecutionResult.Throw<V>) result).getException();
                    if (exception == null) {
                        throw new IllegalArgumentException(
                                "Exception for ExecutionResult.Throw is null, but destination has an exception " +
                                "variable");
                    }
                    newState = ImmutableMap.<LocalVariable, V>builder()
                            .putAll(Maps.filterKeys(priorState, k -> {
                                assert k != null;
                                return !k.equals(exceptionVariable);
                            }))
                            .put(exceptionVariable, exception)
                            .build();
                }
                goTo(target, newState);
                return target;
            } else {
                throw new AssertionError(result.getClass().getName());
            }
        }

        private void goTo(ContinueTarget nextNode, Map<LocalVariable, V> state) {
            if (nextNode.destination.stateCollector.update(nextNode.marker, state)) {
                log.trace("{} updated state from from {}, marking dirty", nextNode, this);
                nextNode.destination.markDirty();
            }
        }

        @Override
        public Collection<Map<LocalVariable, V>> getInput() {
            return Collections.unmodifiableCollection(consolidate);
        }

        @Override
        public Collection<InstructionNode<V>> getNextNodes() {
            return previousTargets.stream().map(t -> t.destination).collect(Collectors.toList());
        }

        @Override
        public String toString() {
            BasicBlock block = instruction.getBlock();
            //noinspection ObjectToString
            StringBuilder builder = new StringBuilder("Node(id=").append(id).append(" block=").append(block)
                    .append(" index=").append(block.getInstructions().indexOf(instruction)).append(" insn=").append(
                            instruction).append(" prior={");
            builder.append(stateCollector.toString(sm -> sm.name));
            builder.append("\n})");
            return builder.toString();
        }
    }

    @Value
    private class ContinueTarget {
        private final Node destination;
        private final SourceMarker marker;
    }

    @RequiredArgsConstructor
    private static class SourceMarker {
        private final String name;
    }
}
