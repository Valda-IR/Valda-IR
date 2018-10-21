package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.Try;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.eclipse.collections.api.block.function.primitive.IntToIntFunction;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.ImmutableTryBlock;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction20t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction30t;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
final class NaiveCodeCompiler {
    static final Map<Class<? extends Instruction>, List<InstructionTemplate<?>>> TEMPLATES =
            Templates.TEMPLATES.stream()
                    .sorted(Comparator.comparingInt(tpl -> Arrays.stream(tpl.formats).mapToInt(f -> f.size).sum()))
                    .collect(Collectors.groupingBy(
                            t -> t.type,
                            Collectors.toList()
                    ));

    private final List<InsnNode<?>> nodes = new ArrayList<>();
    private final Map<BasicBlock, InsnNode<?>> blocks = new HashMap<>();

    private final Queue<BasicBlock> blockQueue = new ArrayDeque<>();
    private final Set<LocalVariable> locals = new HashSet<>();

    private final List<LocalVariable> parameters;

    private int registerCount;

    @SuppressWarnings("NewMethodNamingConvention")
    void add(BasicBlock block) {
        blockQueue.add(block);
        while (true) {
            BasicBlock b = blockQueue.poll();
            if (b == null) { break; }
            prepare(b);
        }
    }

    private void prepare(BasicBlock block) {
        if (blocks.containsKey(block)) { return; }
        if (!block.isTerminated()) { throw new IllegalArgumentException("Block is not terminated"); }
        InsnNode<?> last = null;
        for (int i = 0; i < block.getInstructions().size(); i++) {
            Instruction instruction = block.getInstructions().get(i);
            LocalVariable exceptionVariable = i == 0 ? block.getExceptionVariable() : null;
            InsnNode<Instruction> node = new InsnNode<>(instruction, exceptionVariable);
            if (i == 0) {
                blocks.put(block, node);
            }
            nodes.add(node);

            node.runOnNode(InsnNode::selectTemplate);
            blockQueue.addAll(node.getBlocks());
            locals.addAll(node.getLocals());

            if (last != null) {
                last.continueTo = node;
            }
            last = node;
        }
        if (block.getTry() != null) {
            for (Try.Catch handler : block.getTry().getHandlers()) {
                blockQueue.add(handler.getHandler());
            }
        }
    }

    private static <K> void updateValues(MutableObjectIntMap<K> collection, IntToIntFunction fun) {
        for (K k : collection.keysView()) {
            collection.put(k, fun.applyAsInt(collection.get(k)));
        }
    }

    private static <K> void updateValues(MutableObjectIntMap<K> collection, int delta) {
        updateValues(collection, i -> i + delta);
    }

    private ObjectIntMap<LocalVariable> computeLocalSlots() {
        MutableObjectIntMap<LocalVariable> localSlots = ObjectIntMaps.mutable.empty();
        List<LocalVariable> localOrder = new ArrayList<>(locals);
        localOrder.removeAll(parameters);
        localOrder.addAll(parameters);
        int reg = 0;
        for (LocalVariable variable : localOrder) {
            localSlots.put(variable, reg);
            reg += width(variable);
        }

        // decide how many registers we need for the variables. With each pass, more nodes may decide that they may
        // need fewer "temporary" registers because registers they need are in reach of their instructions already.
        int oldWorkArea = 0x10000 - reg;
        MutableObjectIntMap<InsnNode<?>> workAllocations = ObjectIntMaps.mutable.empty();
        updateValues(localSlots, oldWorkArea);
        while (true) {
            int newWorkArea = 0;
            for (InsnNode<?> node : nodes) {
                int alloc = workAllocations.getIfAbsent(node, -1);
                if (alloc == -1 || oldWorkArea == alloc) {
                    node.runOnNode(n -> workAllocations.put(n, n.computeWorkAllocation(localSlots)));
                }
                newWorkArea = Math.max(newWorkArea, workAllocations.getOrThrow(node));
                if (oldWorkArea != -1 && newWorkArea >= oldWorkArea) {
                    break;
                }
            }
            int delta = newWorkArea - oldWorkArea;
            if (delta >= 0) { break; }
            updateValues(localSlots, delta);
            oldWorkArea = newWorkArea;
        }

        // finalize
        for (InsnNode<?> node : nodes) {
            node.runOnNode(n -> n.computeWorkAllocation(localSlots));
        }

        this.registerCount = reg + oldWorkArea;
        return localSlots;
    }

    ImmutableMethodImplementation compile() {
        for (InsnNode<?> node : nodes) {
            node.computeContinueTo(blocks::get);
        }

        ObjectIntMap<LocalVariable> localSlots = computeLocalSlots();

        for (InsnNode<?> node : nodes) {
            node.runOnNode(n -> n.prepareMoves(localSlots));
            for (ConstrainedNodeDependency dependency : node.computeDependencies(blocks::get)) {
                dependency.from.dependencies.add(dependency);
                dependency.to.dependencies.add(dependency);
            }
        }

        InstructionOrderer orderer = new InstructionOrderer(nodes);
        orderer.run();

        // compute the offsets of all instructions, including their gotos (for continueTo). This is done iteratively,
        // with each pass attempting to reduce the "size" of the gotos (8, 16 or 32 bits).

        // take the initial (pessimistic) predictions from the orderer
        int end = -1;
        while (true) {
            int off = 0;
            for (InsnNode<?> node : orderer.nodesOrdered) {
                node.offset = off;
                if (node.needsGoto) {
                    assert node.continueTo != null; // else needsGoto should be false
                    int delta = node.continueTo.offset - (off + node.getSize());
                    // width of the goto instruction for various deltas
                    if (delta == 0) {
                        // nop + goto
                        off += 2;
                    } else if ((byte) delta == delta) {
                        off += 1;
                    } else if ((short) delta == delta) {
                        off += 2;
                    } else {
                        off += 3;
                    }
                }
                off += node.getSize();
            }
            if (off == end) {
                break;
            } else {
                end = off;
            }
        }

        CodeOutput output = new CodeOutput(end);

        for (InsnNode<?> node : orderer.nodesOrdered) {
            output.expectOffset(node.offset);
            node.runOnNode(n -> n.compile(output, localSlots, b -> blocks.get(b).offset - node.offset));
            if (node.needsGoto) {
                assert node.continueTo != null;
                int delta = node.continueTo.offset - (node.offset + node.getSize());
                if (delta == 0) {
                    output.pushInsn(new ImmutableInstruction10x(Opcode.NOP));
                    output.pushInsn(new ImmutableInstruction10t(Opcode.GOTO, -1));
                } else if ((byte) delta == delta) {
                    output.pushInsn(new ImmutableInstruction10t(Opcode.GOTO, delta));
                } else if ((short) delta == delta) {
                    output.pushInsn(new ImmutableInstruction20t(Opcode.GOTO_16, delta));
                } else {
                    output.pushInsn(new ImmutableInstruction30t(Opcode.GOTO_32, delta));
                }
            }
        }

        // cache for try -> catch handler. Catch handlers are merged by identity (not equality) later, so we try to
        // keep as few as possible
        Map<Try, InsnCatchHandlers> catchHandlers = new IdentityHashMap<>();
        for (InsnNode<?> node : orderer.nodesOrdered) {
            if (node.canThrow()) {
                Try try_ = node.getTry();
                if (try_ == null) {
                    node.catchHandlers = null;
                } else {
                    node.catchHandlers = catchHandlers.get(try_);
                    if (node.catchHandlers == null) {
                        InsnCatchHandlers here = new InsnCatchHandlers(try_, blocks::get);
                        // try to find another identical catch handler
                        for (InsnCatchHandlers there : catchHandlers.values()) {
                            if (here.equals(there)) {
                                here = there;
                                break;
                            }
                        }
                        catchHandlers.put(try_, here);
                        node.catchHandlers = here;
                    }
                }
            }
        }

        ImmutableList.Builder<ImmutableTryBlock> tryBlocks = ImmutableList.builder();
        InsnNode<?> currentStartNode = null;
        InsnNode<?> currentEndNode = null;
        InsnCatchHandlers currentHandlers = null;
        for (InsnNode<?> insnNode : orderer.nodesOrdered) {
            if (insnNode.canThrow()) {
                //noinspection ObjectEquality
                if (insnNode.catchHandlers != currentHandlers ||
                    // catch blocks can only be ~1^16 code units long, end well before that.w
                    (currentStartNode != null &&
                                 currentEndNode.offset + 100 - currentStartNode.offset > (1 << 16))) {
                    if (currentHandlers != null) {
                        tryBlocks.add(currentHandlers.toTryBlock(currentStartNode, currentEndNode));
                    }
                    currentStartNode = insnNode;
                    currentEndNode = insnNode;
                    currentHandlers = insnNode.catchHandlers;
                } else {
                    currentEndNode = insnNode;
                }
            }
        }
        if (currentHandlers != null) {
            tryBlocks.add(currentHandlers.toTryBlock(currentStartNode, currentEndNode));
        }

        return new ImmutableMethodImplementation(registerCount, output.finish(), tryBlocks.build(), null);
    }

    static int width(LocalVariable variable) {
        return variable.getType() == LocalVariable.Type.WIDE ? 2 : 1;
    }
}
