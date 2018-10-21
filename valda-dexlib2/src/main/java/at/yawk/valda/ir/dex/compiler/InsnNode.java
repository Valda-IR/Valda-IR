package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.TerminatingInstruction;
import at.yawk.valda.ir.code.Try;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.collections.api.block.procedure.primitive.IntObjectProcedure;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.instruction.PayloadInstruction;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction12x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction32x;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
class InsnNode<I extends Instruction> {
    static final int UNKNOWN_OFFSET = -1;

    private final I hlInsn;
    @Nullable private final LocalVariable exceptionVariable;

    private InstructionTemplate<I> template;
    @Getter private Set<LocalVariable> locals;
    @Getter private Set<BasicBlock> blocks;
    private List<AllocationGroup> allocationRequests;
    private Set<AllocationGroup> directGroups;

    @Getter @Accessors(fluent = true) private boolean canThrow;

    @Nullable InsnNode<?> continueTo;
    @Nullable InsnCatchHandlers catchHandlers;
    final List<ConstrainedNodeDependency> dependencies = new ArrayList<>();
    boolean needsGoto;
    int offset = UNKNOWN_OFFSET;

    private List<org.jf.dexlib2.iface.instruction.Instruction> prologue;
    private List<org.jf.dexlib2.iface.instruction.Instruction> epilogue;
    @Getter private int size;

    private static <I> void forEach(
            I insn,
            List<InstructionTemplate.Locals<I>> list,
            IntObjectProcedure<LocalVariable> task
    ) {
        for (InstructionTemplate.Locals<I> l : list) {
            for (LocalVariable variable : l.getLocals().apply(insn)) {
                task.value(l.getRegisterBits(), variable);
            }
        }
    }

    void runOnNode(Consumer<InsnNode<I>> op) {
        try {
            op.accept(this);
        } catch (InstructionCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new InstructionCompileException(hlInsn, e);
        }
    }

    void selectTemplate() {
        List<InstructionTemplate<?>> templateList = NaiveCodeCompiler.TEMPLATES.get(hlInsn.getClass());
        if (templateList == null) {
            throw new InstructionCompileException(hlInsn, "No template available");
        }
        outer:
        for (InstructionTemplate<?> rawTemplate : templateList) {
            @SuppressWarnings("unchecked")
            InstructionTemplate<I> template = (InstructionTemplate<I>) rawTemplate;
            for (Predicate<I> precondition : template.preconditions) {
                if (!precondition.test(hlInsn)) {
                    continue outer;
                }
            }
            if (template.sameRegister != null) {
                if (!template.sameRegister.getA().apply(hlInsn).equals(
                        template.sameRegister.getB().apply(hlInsn))) {
                    continue;
                }
            }
            // don't support non-jumbo string consts for now.
            if (template.stringRefs.stream().anyMatch(s -> s.getReferenceWidth() < 32)) {
                continue;
            }

            this.template = template;
            break;
        }

        if (template == null) {
            throw new InstructionCompileException(hlInsn, "No template matches");
        }

        blocks = template.blockRefs.stream()
                .flatMap(c -> c.getBlocks().apply(hlInsn).stream())
                .collect(Collectors.toCollection(HashSet::new));
        if (template.continueTo != null) {
            blocks.add(template.continueTo.apply(hlInsn));
        }

        allocationRequests = new ArrayList<>();
        forEach(hlInsn, template.inLocals, (registerBits, local) -> {
            for (AllocationGroup group : allocationRequests) {
                for (RegisterAllocationRequest req : group.requests) {
                    if (req.variable.equals(local)) {
                        assert req.in;
                        req.registerBits = Math.min(registerBits, req.registerBits);
                        return;
                    }
                }
            }
            allocationRequests.add(new AllocationGroup(Collections.singletonList(
                    new RegisterAllocationRequest(registerBits, local, true, false))));
        });
        forEach(hlInsn, template.outLocals, (registerBits, local) -> {
            for (AllocationGroup group : allocationRequests) {
                for (RegisterAllocationRequest req : group.requests) {
                    if (req.variable.equals(local)) {
                        req.out = true;
                        req.registerBits = Math.min(registerBits, req.registerBits);
                        return;
                    }
                }
            }
            allocationRequests.add(new AllocationGroup(Collections.singletonList(
                    new RegisterAllocationRequest(registerBits, local, false, true))));
        });
        forEach(hlInsn, template.outLocalsNoOverlap, (registerBits, local) -> {
            for (AllocationGroup group : allocationRequests) {
                for (RegisterAllocationRequest req : group.requests) {
                    if (req.variable.equals(local) && !req.in) {
                        req.registerBits = Math.min(registerBits, req.registerBits);
                        return;
                    }
                }
            }
            allocationRequests.add(new AllocationGroup(Collections.singletonList(
                    new RegisterAllocationRequest(registerBits, local, false, true))));
        });
        forEach(hlInsn, template.tmpLocals, (registerBits, local) ->
                allocationRequests.add(new AllocationGroup(Collections.singletonList(
                        new RegisterAllocationRequest(registerBits, local, false, false)))));

        if (template.contiguous != null) {
            List<LocalVariable> ins = template.contiguous.getIns().apply(hlInsn);
            List<RegisterAllocationRequest> contiguousRequests = new ArrayList<>(ins.size() + 1);
            if (template.contiguous.getFirstOut() != null) {
                LocalVariable firstOut = template.contiguous.getFirstOut().apply(hlInsn);
                RegisterAllocationRequest request = null;
                for (Iterator<AllocationGroup> iterator = allocationRequests.iterator(); iterator.hasNext(); ) {
                    AllocationGroup group = iterator.next();
                    if (group.requests.size() != 1) { continue; }
                    RegisterAllocationRequest here = group.requests.get(0);
                    if (here.variable.equals(firstOut) && !here.in) {
                        request = here;
                        iterator.remove();
                        break;
                    }
                }
                if (request == null) { throw new AssertionError(); }
                contiguousRequests.add(request);
            }
            for (LocalVariable local : ins) {
                RegisterAllocationRequest request = null;
                for (Iterator<AllocationGroup> iterator = allocationRequests.iterator(); iterator.hasNext(); ) {
                    AllocationGroup group = iterator.next();
                    if (group.requests.size() != 1) { continue; }
                    RegisterAllocationRequest here = group.requests.get(0);
                    if (here.variable.equals(local) && here.in) {
                        request = here;
                        iterator.remove();
                        break;
                    }
                }
                if (request == null) {
                    // can happen if the local appears multiple times
                    request = new RegisterAllocationRequest(32, local, true, false);
                }
                contiguousRequests.add(request);
            }
            if (!contiguousRequests.isEmpty()) {
                allocationRequests.add(new AllocationGroup(contiguousRequests));
            }
        }
        allocationRequests.sort(AllocationGroup.COMPARATOR);

        locals = allocationRequests.stream().flatMap(g -> g.requests.stream())
                .map(req -> req.variable).collect(Collectors.toCollection(HashSet::new));
        if (exceptionVariable != null) {
            locals.add(exceptionVariable);
        }
    }

    void computeContinueTo(Function<BasicBlock, InsnNode<?>> nodeByBlock) {
        if (hlInsn instanceof TerminatingInstruction) {
            if (continueTo != null) { throw new AssertionError(); }

            continueTo = template.continueTo == null ? null : nodeByBlock.apply(template.continueTo.apply(hlInsn));
        } else {
            if (continueTo == null) { throw new AssertionError(); }
        }
    }

    Try getTry() {
        return hlInsn.getBlock().getTry();
    }

    int computeWorkAllocation(ObjectIntMap<LocalVariable> localSlots) {
        directGroups = new HashSet<>();

        int workAllocationSize = 0;
        for (AllocationGroup group : Lists.reverse(allocationRequests)) {
            workAllocationSize += group.size;

            if (group.requests.size() != 1) { continue; }
            RegisterAllocationRequest request = group.requests.get(0);
            int localSlot = localSlots.get(request.variable);
            if ((1 << request.registerBits) <= localSlot) { continue; }
            if (directGroups.stream().anyMatch(g -> g.requests.get(0).variable.equals(request.variable))) {
                continue;
            }

            // don't need to move \o/
            directGroups.add(group);
            workAllocationSize -= group.size;
        }

        if (workAllocationSize == 0 && exceptionVariable != null &&
            localSlots.getOrThrow(exceptionVariable) > 0xff) {
            workAllocationSize = 1;
        }
        return workAllocationSize;
    }

    Collection<ConstrainedNodeDependency> computeDependencies(Function<BasicBlock, InsnNode<?>> getBlockInsn) {
        List<InstructionTemplate.BlockRefs<I>> refs = template.blockRefs;
        if (refs.isEmpty()) { return Collections.emptySet(); }
        List<ConstrainedNodeDependency> dependencies = new ArrayList<>();
        for (InstructionTemplate.BlockRefs<I> blockRef : template.blockRefs) {
            if (blockRef.getReferenceWidth() >= 32) { continue; }
            for (BasicBlock block : blockRef.getBlocks().apply(hlInsn)) {
                dependencies.add(new ConstrainedNodeDependency(
                        blockRef.getReferenceWidth(), this, getBlockInsn.apply(block)));
            }
        }
        return dependencies;
    }

    void prepareMoves(ObjectIntMap<LocalVariable> localSlots) {
        prologue = new ArrayList<>();
        epilogue = new ArrayList<>();

        if (exceptionVariable != null) {
            int to = localSlots.getOrThrow(exceptionVariable);
            if (to > 0xff) {
                prologue.add(new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, 0));
                prologue.add(move(LocalVariable.Type.REFERENCE, 0, to));
            } else {
                prologue.add(new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, to));
            }
        }

        int reg = 0;
        for (AllocationGroup group : allocationRequests) {
            if (directGroups.contains(group)) { continue; }
            for (RegisterAllocationRequest request : group.requests) {
                int localSlot = localSlots.getOrThrow(request.variable);
                if (request.in) { prologue.add(move(request.variable.getType(), localSlot, reg)); }
                if (request.out) { epilogue.add(move(request.variable.getType(), reg, localSlot)); }
                reg += request.size();
            }
        }

        this.size = prologue.stream().mapToInt(org.jf.dexlib2.iface.instruction.Instruction::getCodeUnits).sum() +
                    Arrays.stream(template.formats).mapToInt(f -> f.size / 2).sum() +
                    epilogue.stream().mapToInt(org.jf.dexlib2.iface.instruction.Instruction::getCodeUnits).sum();
    }

    void compile(CodeOutput output, ObjectIntMap<LocalVariable> localSlots, ToIntFunction<BasicBlock> offsetToBlock) {
        prologue.forEach(output::pushInsn);
        int prologueSize = prologue.stream().mapToInt(org.jf.dexlib2.iface.instruction.Instruction::getCodeUnits).sum();
        InstructionContext ctx = new InstructionContext() {
            private int findEntry(LocalVariable variable, Predicate<RegisterAllocationRequest> filter) {
                int reg = 0;
                for (AllocationGroup group : allocationRequests) {
                    boolean direct = directGroups.contains(group);
                    for (RegisterAllocationRequest request : group.requests) {
                        if (request.variable.equals(variable) && filter.test(request)) {
                            if (direct) {
                                return localSlots.get(variable);
                            } else {
                                return reg;
                            }
                        }
                        if (!direct) {
                            reg += request.size();
                        }
                    }
                }
                throw new IllegalStateException();
            }

            @Override
            public int inReg(LocalVariable variable) {
                return findEntry(variable, request -> request.in);
            }

            @Override
            public int outReg(LocalVariable variable) {
                return findEntry(variable, request -> request.out);
            }

            @Override
            public int outRegNoOverlap(LocalVariable variable) {
                return findEntry(variable, request -> request.out && !request.in);
            }

            @Override
            public int tmpReg(LocalVariable variable) {
                return findEntry(variable, request -> !request.in && !request.out);
            }

            @Override
            public int offsetToPayload(PayloadInstruction payload) {
                return output.pushFooter(payload);
            }

            @Override
            public int offsetToBlock(BasicBlock block) {
                return offsetToBlock.applyAsInt(block) - prologueSize;
            }
        };
        org.jf.dexlib2.iface.instruction.Instruction[] instructions = template.compile.apply(ctx, hlInsn);
        Format[] formats = template.formats;
        if (formats.length != instructions.length) {
            throw new InstructionCompileException(hlInsn, "Unexpected instruction count");
        }
        canThrow = false;
        for (int j = 0; j < instructions.length; j++) {
            if (instructions[j].getOpcode().format != formats[j]) {
                throw new InstructionCompileException(hlInsn, "Unexpected format at index " + j);
            }
            output.pushInsn(instructions[j]);
            canThrow |= instructions[j].getOpcode().canThrow();
        }
        epilogue.forEach(output::pushInsn);
    }

    private static org.jf.dexlib2.iface.instruction.Instruction move(LocalVariable.Type type, int from, int to) {
        if (to < 0xf) {
            if (from < 0xf) {
                switch (type) {
                    case NARROW:
                        return new ImmutableInstruction12x(Opcode.MOVE, to, from);
                    case WIDE:
                        return new ImmutableInstruction12x(Opcode.MOVE_WIDE, to, from);
                    case REFERENCE:
                        return new ImmutableInstruction12x(Opcode.MOVE_OBJECT, to, from);
                    default:
                        throw new AssertionError();
                }
            } else {
                switch (type) {
                    case NARROW:
                        return new ImmutableInstruction22x(Opcode.MOVE_FROM16, to, from);
                    case WIDE:
                        return new ImmutableInstruction22x(Opcode.MOVE_WIDE_FROM16, to, from);
                    case REFERENCE:
                        return new ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, to, from);
                    default:
                        throw new AssertionError();
                }
            }
        } else {
            switch (type) {
                case NARROW:
                    return new ImmutableInstruction32x(Opcode.MOVE_16, to, from);
                case WIDE:
                    return new ImmutableInstruction32x(Opcode.MOVE_WIDE_16, to, from);
                case REFERENCE:
                    return new ImmutableInstruction32x(Opcode.MOVE_OBJECT_16, to, from);
                default:
                    throw new AssertionError();
            }
        }
    }
}
