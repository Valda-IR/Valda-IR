package at.yawk.valda.ir.dex.parser;

import at.yawk.valda.ir.ArrayTypeMirror;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.ExternalTypeMirror;
import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.NoSuchMemberException;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.code.ArrayLength;
import at.yawk.valda.ir.code.ArrayLoadStore;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.FillArray;
import at.yawk.valda.ir.code.GoTo;
import at.yawk.valda.ir.code.InstanceOf;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.Monitor;
import at.yawk.valda.ir.code.Move;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Return;
import at.yawk.valda.ir.code.Switch;
import at.yawk.valda.ir.code.TerminatingInstruction;
import at.yawk.valda.ir.code.Throw;
import at.yawk.valda.ir.code.Try;
import at.yawk.valda.ir.code.UnaryOperation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.api.PrimitiveIterable;
import org.eclipse.collections.api.list.primitive.MutableByteList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.list.primitive.MutableShortList;
import org.eclipse.collections.impl.factory.primitive.ByteLists;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.factory.primitive.ShortLists;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction;
import org.jf.dexlib2.iface.instruction.SwitchElement;
import org.jf.dexlib2.iface.instruction.SwitchPayload;
import org.jf.dexlib2.iface.instruction.ThreeRegisterInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.instruction.VariableRegisterInstruction;
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction;
import org.jf.dexlib2.iface.instruction.formats.ArrayPayload;
import org.jf.dexlib2.iface.instruction.formats.Instruction11x;
import org.jf.dexlib2.iface.instruction.formats.Instruction22b;
import org.jf.dexlib2.iface.instruction.formats.Instruction22s;
import org.jf.dexlib2.iface.instruction.formats.Instruction31t;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;
import org.jf.dexlib2.iface.instruction.formats.Instruction3rc;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@Slf4j
final class InstructionParser {
    private final Classpath classpath;
    private final InstructionList instructions;
    private final TypeChecker typeChecker;

    private final List<BlockBounds> blockBounds;
    private final List<BlockParser> blockParsers = new ArrayList<>();
    private final Queue<BlockParser> populateQueue = new ArrayDeque<>();

    DexParserErrorHandler errorHandler = DexParserErrorHandler.getDefault();

    InstructionParser(Classpath classpath, InstructionList instructions, TypeChecker typeChecker) {
        this.classpath = classpath;
        this.instructions = instructions;
        this.typeChecker = typeChecker;

        blockBounds = ImmutableList.copyOf(typeChecker.getBlocks());
    }

    static LocalVariable registerVariable(int register, LocalVariable.Type type) {
        if (register < 0) { throw new IllegalArgumentException(); }
        return LocalVariable.create(type, "r" + register + "/" + type);
    }

    void addTry(TryBlock<? extends ExceptionHandler> tryBlock) {
        Try try_ = new Try();
        for (ExceptionHandler exceptionHandler : tryBlock.getExceptionHandlers()) {
            BlockParser parser = getBlockParser(getBlockIndexAtInstruction(
                    instructions.getInstructionIndexAtOffset(exceptionHandler.getHandlerCodeAddress())));
            parser.isCatchHandler = true;
            Try.Catch c = try_.addCatch(parser.startBlock);
            if (exceptionHandler.getExceptionType() != null) {
                c.setExceptionType(classpath.getTypeMirror(Type.getType(exceptionHandler.getExceptionType())));
            }
        }

        int i = getBlockIndexAtInstruction(instructions.getInstructionIndexAtOffset(tryBlock.getStartCodeAddress()));
        int endInsn = instructions.getInstructionIndexAtOffset(
                tryBlock.getStartCodeAddress() + tryBlock.getCodeUnitCount());
        int end = endInsn == instructions.getInstructionCount() ?
                blockBounds.size() : getBlockIndexAtInstruction(endInsn);
        for (; i < end; i++) {
            BasicBlock basicBlock = getBlockParser(i).startBlock;
            if (basicBlock.getTry() != null) { throw new DexVerifyException("Overlapping try"); }
            basicBlock.setTry(try_);
        }
    }

    @SuppressWarnings("NewMethodNamingConvention")
    BasicBlock run() {
        BasicBlock firstBlock = getBlockParser(0).startBlock;
        while (true) {
            BlockParser next = populateQueue.poll();
            if (next == null) { break; }
            next.buildDirectReferences();
        }
        for (BlockParser parser : blockParsers) {
            if (parser == null) { continue; }
            parser.populate();
        }
        return firstBlock;
    }

    private BlockParser getBlockParser(int blockIndex) {
        while (blockParsers.size() <= blockIndex) { blockParsers.add(null); }
        BlockParser block = blockParsers.get(blockIndex);
        if (block == null) {
            block = new BlockParser(blockIndex, blockBounds.get(blockIndex));
            blockParsers.set(blockIndex, block);
            populateQueue.add(block);
        }
        return block;
    }

    private int getBlockIndexAtInstruction(int index) {
        for (int i = 0; i < blockBounds.size(); i++) {
            BlockBounds block = blockBounds.get(i);
            if (block.getStartIndex() == index) {
                return i;
            }
        }

        throw new NoSuchElementException("No block starts at " + index);
    }

    @RequiredArgsConstructor
    private class BlockParser {
        private final int blockIndex;
        private final BasicBlock startBlock = BasicBlock.create();
        /**
         * The current block to add instructions to. May be dead code, or may be reachable from {@link #startBlock}.
         */
        private BasicBlock workBlock = startBlock;
        private final BlockBounds bounds;

        /**
         * Set of direct references from this block (i.e. non-catch references)
         */
        private final Set<BlockParser> directReferences = new HashSet<>();
        private boolean explicitlyTerminated;
        private boolean isCatchHandler = false;
        @Nullable private LocalVariable startsWithMoveResultVariable = null;

        private boolean needsPopulate = true;
        private int i;

        void buildDirectReferences() {
            i = bounds.getStartIndex() + bounds.getInstructionCount() - 1;
            TerminatingInstruction terminating = translateTerminating();
            explicitlyTerminated = terminating != null;
            if (terminating == null) {
                makeReference(i + 1);
            }
            i = bounds.getStartIndex();
            translateMoveResult();
        }

        void populate() {
            if (!needsPopulate) { throw new IllegalStateException(); }
            needsPopulate = false;

            for (i = bounds.getStartIndex(); i < bounds.getStartIndex() + bounds.getInstructionCount(); i++) {
                at.yawk.valda.ir.code.Instruction translated = translate();
                if (translated != null) {
                    workBlock.addInstruction(translated);
                }
            }
            if (!workBlock.isTerminated()) {
                assert instructions.getInstruction(bounds.getStartIndex() + bounds.getInstructionCount() - 1)
                        .getOpcode().canContinue();
                workBlock.addInstruction(GoTo.create(getBlockParser(blockIndex + 1).startBlock));
            }
        }

        private LocalVariable reg(int register, LocalVariable.Type type) {
            return registerVariable(register, type);
        }

        private LocalVariable regA(OneRegisterInstruction instruction, LocalVariable.Type type) {
            return reg(instruction.getRegisterA(), type);
        }

        private LocalVariable regB(TwoRegisterInstruction instruction, LocalVariable.Type type) {
            return reg(instruction.getRegisterB(), type);
        }

        private LocalVariable regC(ThreeRegisterInstruction instruction, LocalVariable.Type type) {
            return reg(instruction.getRegisterC(), type);
        }

        private UnaryOperation unaryOp(
                UnaryOperation.Type type,
                TwoRegisterInstruction instruction
        ) {
            return UnaryOperation.builder().type(type)
                    .destination(regA(instruction, type.getOutType()))
                    .source(regB(instruction, type.getOperandType())).build();
        }

        private BinaryOperation binaryOp(
                BinaryOperation.Type type,
                ThreeRegisterInstruction instruction
        ) {
            return BinaryOperation.builder().type(type)
                    .destination(regA(instruction, type.getOutType()))
                    .lhs(regB(instruction, type.getLhsType()))
                    .rhs(regC(instruction, type.getRhsType()))
                    .build();
        }

        private BinaryOperation binaryOpInPlace(
                BinaryOperation.Type type,
                TwoRegisterInstruction instruction
        ) {
            return BinaryOperation.builder().type(type)
                    .destination(regA(instruction, type.getOutType()))
                    .lhs(regA(instruction, type.getLhsType()))
                    .rhs(regB(instruction, type.getRhsType()))
                    .build();
        }

        private <I extends TwoRegisterInstruction & NarrowLiteralInstruction> LiteralBinaryOperation binaryOpLiteral(
                LiteralBinaryOperation.Type type,
                I instruction
        ) {
            return LiteralBinaryOperation.builder().type(type)
                    .destination(regA(instruction, LocalVariable.Type.NARROW))
                    .lhs(regB(instruction, LocalVariable.Type.NARROW))
                    .rhs((short) instruction.getNarrowLiteral())
                    .build();
        }

        private TypeMirror type(ReferenceInstruction instruction) {
            return classpath.getTypeMirror(
                    Type.getType(((TypeReference) instruction.getReference()).getType())
            );
        }

        private FieldMirror field(ReferenceInstruction instruction, boolean isStatic) {
            FieldReference ref = (FieldReference) instruction.getReference();
            return resolveField(classpath, ref, TriState.valueOf(isStatic));
        }

        private BasicBlock makeOffsetReference(int codeOffset) {
            return makeReference(instructions.getInstructionIndexAtOffset(
                    instructions.getOffset(i) + codeOffset));
        }

        private BasicBlock makeReference(int index) {
            BlockParser next = getBlockParser(getBlockIndexAtInstruction(index));
            this.directReferences.add(next);
            return next.startBlock;
        }

        private TerminatingInstruction branch(
                Branch.Type type,
                boolean branchOnTrue,
                TwoRegisterInstruction instruction
        ) {
            BasicBlock far = makeOffsetReference(((OffsetInstruction) instruction).getCodeOffset());
            BasicBlock near = makeReference(i + 1);
            RegisterType registerTypeA = typeChecker.getRegisterInputTypes(i).get(instruction.getRegisterA());
            if (registerTypeA != RegisterType.NARROW && registerTypeA != RegisterType.REFERENCE &&
                registerTypeA != RegisterType.ZERO && registerTypeA != RegisterType.REFERENCE_UNINITIALIZED) {
                throw new DexVerifyException("Can only if references and narrow registers");
            }
            RegisterType registerTypeB = typeChecker.getRegisterInputTypes(i).get(instruction.getRegisterB());
            RegisterType actualRegisterType;
            if (registerTypeA == RegisterType.ZERO) {
                if (registerTypeB == RegisterType.ZERO) {
                    // both are zero - just precompute jump...
                    return branchPrecomputedCmpZero(type, branchOnTrue, far, near);
                } else {
                    actualRegisterType = registerTypeB;
                }
            } else {
                actualRegisterType = registerTypeA;
            }
            LocalVariable.Type varType = actualRegisterType == RegisterType.NARROW ?
                    LocalVariable.Type.NARROW : LocalVariable.Type.REFERENCE;
            return Branch.builder().type(type)
                    .lhs(regA(instruction, varType))
                    .rhs(regB(instruction, varType))
                    .branchTrue(branchOnTrue ? far : near)
                    .branchFalse(branchOnTrue ? near : far)
                    .build();
        }

        private TerminatingInstruction branchZero(
                Branch.Type type, boolean branchOnTrue,
                OneRegisterInstruction instruction
        ) {
            BasicBlock far = makeOffsetReference(((OffsetInstruction) instruction).getCodeOffset());
            BasicBlock near = makeReference(i + 1);
            RegisterType registerType = typeChecker.getRegisterInputTypes(i).get(instruction.getRegisterA());
            if (registerType == RegisterType.ZERO) {
                return branchPrecomputedCmpZero(type, branchOnTrue, far, near);
            }
            if (registerType != RegisterType.NARROW && registerType != RegisterType.REFERENCE &&
                registerType != RegisterType.REFERENCE_UNINITIALIZED) {
                throw new DexVerifyException("Can only if_z references and narrow registers");
            }
            return Branch.builder().type(type)
                    .lhs(regA(instruction,
                              registerType == RegisterType.NARROW ?
                                      LocalVariable.Type.NARROW :
                                      LocalVariable.Type.REFERENCE))
                    .rhsZero()
                    .branchTrue(branchOnTrue ? far : near)
                    .branchFalse(branchOnTrue ? near : far)
                    .build();
        }

        private TerminatingInstruction branchPrecomputedCmpZero(
                Branch.Type type,
                boolean branchOnTrue,
                BasicBlock far,
                BasicBlock near
        ) {
            switch (type) {
                case EQUAL:
                    return GoTo.create(branchOnTrue ? far : near);
                case GREATER_THAN:
                case LESS_THAN:
                    return GoTo.create(branchOnTrue ? near : far);
                default:
                    throw new AssertionError();
            }
        }

        @Nullable
        private LocalVariable findReturnVariable() {
            int nextI = i + 1;
            if (nextI >= bounds.getStartIndex() + bounds.getInstructionCount() - (explicitlyTerminated ? 1 : 0)) {
                Set<LocalVariable> locals = new HashSet<>();
                for (BlockParser directReference : directReferences) {
                    locals.add(directReference.startsWithMoveResultVariable);
                }
                if (locals.size() > 1) {
                    throw new DexVerifyException("Unsupported move-results at start of blocks");
                }
                return locals.isEmpty() ? null : locals.iterator().next();
            }
            Instruction next = instructions.getInstruction(i + 1);
            switch (next.getOpcode()) {
                case MOVE_RESULT: {
                    i++;
                    return regA((OneRegisterInstruction) next, LocalVariable.Type.NARROW);
                }
                case MOVE_RESULT_WIDE: {
                    i++;
                    return regA((OneRegisterInstruction) next, LocalVariable.Type.WIDE);
                }
                case MOVE_RESULT_OBJECT: {
                    i++;
                    return regA((OneRegisterInstruction) next, LocalVariable.Type.REFERENCE);
                }
                default: {
                    return null;
                }
            }
        }

        @Nullable
        private NewArray newArray(ArrayTypeMirror type, List<LocalVariable> contents) {
            LocalVariable returnVariable = findReturnVariable();
            if (returnVariable == null) {
                return null;
            } else {
                // should be type-checked
                assert returnVariable.getType() == LocalVariable.Type.REFERENCE;
                return NewArray.variableBuilder().target(returnVariable).type(type).variables(contents).build();
            }
        }

        @Nullable
        private TerminatingInstruction translateTerminating() {
            Instruction instruction = instructions.getInstruction(i);
            switch (instruction.getOpcode()) {
                case RETURN_VOID: {
                    return Return.createVoid();
                }
                case RETURN: {
                    return Return.create(regA((Instruction11x) instruction, LocalVariable.Type.NARROW));
                }
                case RETURN_WIDE: {
                    return Return.create(regA((Instruction11x) instruction, LocalVariable.Type.WIDE));
                }
                case RETURN_OBJECT: {
                    return Return.create(regA((Instruction11x) instruction, LocalVariable.Type.REFERENCE));
                }
                case THROW: {
                    return Throw.create(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE));
                }
                case GOTO:
                case GOTO_16:
                case GOTO_32: {
                    return GoTo.create(makeOffsetReference(((OffsetInstruction) instruction).getCodeOffset()));
                }
                case PACKED_SWITCH:
                case SPARSE_SWITCH: {
                    SwitchPayload payload = (SwitchPayload) instructions.getInstructionAtOffset(
                            instructions.getOffset(i) + ((OffsetInstruction) instruction).getCodeOffset());
                    Switch sw = Switch.create(regA((OneRegisterInstruction) instruction, LocalVariable.Type.NARROW),
                                              makeReference(i + 1));
                    for (SwitchElement element : payload.getSwitchElements()) {
                        sw.addBranch(element.getKey(), makeOffsetReference(element.getOffset()));
                    }
                    return sw;
                }

                case IF_EQ: {
                    return branch(Branch.Type.EQUAL, true, (TwoRegisterInstruction) instruction);
                }
                case IF_NE: {
                    return branch(Branch.Type.EQUAL, false, (TwoRegisterInstruction) instruction);
                }
                case IF_GT: {
                    return branch(Branch.Type.GREATER_THAN, true, (TwoRegisterInstruction) instruction);
                }
                case IF_LE: {
                    return branch(Branch.Type.GREATER_THAN, false, (TwoRegisterInstruction) instruction);
                }
                case IF_LT: {
                    return branch(Branch.Type.LESS_THAN, true, (TwoRegisterInstruction) instruction);
                }
                case IF_GE: {
                    return branch(Branch.Type.LESS_THAN, false, (TwoRegisterInstruction) instruction);
                }

                case IF_EQZ: {
                    return branchZero(Branch.Type.EQUAL, true, (OneRegisterInstruction) instruction);
                }
                case IF_NEZ: {
                    return branchZero(Branch.Type.EQUAL, false, (OneRegisterInstruction) instruction);
                }
                case IF_GTZ: {
                    return branchZero(Branch.Type.GREATER_THAN, true, (OneRegisterInstruction) instruction);
                }
                case IF_LEZ: {
                    return branchZero(Branch.Type.GREATER_THAN, false, (OneRegisterInstruction) instruction);
                }
                case IF_LTZ: {
                    return branchZero(Branch.Type.LESS_THAN, true, (OneRegisterInstruction) instruction);
                }
                case IF_GEZ: {
                    return branchZero(Branch.Type.LESS_THAN, false, (OneRegisterInstruction) instruction);
                }
            }
            return null;
        }

        private boolean translateMoveResult() {
            // this method throws if the instruction does not appear at the start of a block. When it doesn't it
            // should've already been skipped over by findReturnValue.

            Instruction instruction = instructions.getInstruction(i);
            switch (instruction.getOpcode()) {
                case MOVE_RESULT: {
                    if (i != bounds.getStartIndex()) {
                        throw new RuntimeException("move-result* without accompanying invoke!");
                    }
                    startsWithMoveResultVariable = regA((OneRegisterInstruction) instruction,
                                                        LocalVariable.Type.NARROW);
                    return true;
                }
                case MOVE_RESULT_WIDE: {
                    if (i != bounds.getStartIndex()) {
                        throw new RuntimeException("move-result* without accompanying invoke!");
                    }
                    startsWithMoveResultVariable = regA((OneRegisterInstruction) instruction, LocalVariable.Type.WIDE);
                    return true;
                }
                case MOVE_RESULT_OBJECT: {
                    if (i != bounds.getStartIndex()) {
                        throw new RuntimeException("move-result* without accompanying invoke!");
                    }
                    startsWithMoveResultVariable = regA((OneRegisterInstruction) instruction,
                                                        LocalVariable.Type.REFERENCE);
                    return true;
                }
            }
            return false;
        }

        private void linkageError(NoSuchMemberException e, Class<? extends IncompatibleClassChangeError> errorType) {
            List<at.yawk.valda.ir.code.Instruction> insns =
                    errorHandler.handleInstructionLinkageError(classpath, e, errorType);
            workBlock.addInstructions(insns);
            if (workBlock.isTerminated()) {
                workBlock = BasicBlock.create();
            }
        }

        @Nullable
        private at.yawk.valda.ir.code.Instruction translate() {
            TerminatingInstruction terminating = translateTerminating();
            if (terminating != null) { return terminating; }

            if (translateMoveResult()) { return null; }

            Instruction instruction = instructions.getInstruction(i);
            switch (instruction.getOpcode()) {
                case NOP:
                    return null;
                case MOVE:
                case MOVE_FROM16:
                case MOVE_16: {
                    TwoRegisterInstruction insn = (TwoRegisterInstruction) instruction;
                    return Move.builder()
                            .from(regB(insn, LocalVariable.Type.NARROW))
                            .to(regA(insn, LocalVariable.Type.NARROW)).build();
                }
                case MOVE_WIDE:
                case MOVE_WIDE_FROM16:
                case MOVE_WIDE_16: {
                    TwoRegisterInstruction insn = (TwoRegisterInstruction) instruction;
                    return Move.builder()
                            .from(regB(insn, LocalVariable.Type.WIDE))
                            .to(regA(insn, LocalVariable.Type.WIDE)).build();
                }
                case MOVE_OBJECT:
                case MOVE_OBJECT_FROM16:
                case MOVE_OBJECT_16: {
                    TwoRegisterInstruction insn = (TwoRegisterInstruction) instruction;
                    if (typeChecker.getRegisterInputTypes(i).get(insn.getRegisterB()) ==
                        RegisterType.REFERENCE_UNINITIALIZED) {
                        // just ignore moves on uninitialized references
                        return null;
                    }
                    return Move.builder()
                            .from(regB(insn, LocalVariable.Type.REFERENCE))
                            .to(regA(insn, LocalVariable.Type.REFERENCE)).build();
                }
                case MOVE_EXCEPTION: {
                    if (i != bounds.getStartIndex()) {
                        throw new DexVerifyException("move-exception not at start of block");
                    }
                    if (!isCatchHandler) {
                        throw new DexVerifyException("move-exception not in catch handler");
                    }
                    startBlock.setExceptionVariable(regA((OneRegisterInstruction) instruction,
                                                         LocalVariable.Type.REFERENCE));
                    return null;
                }
                case CONST:
                case CONST_4:
                case CONST_16:
                case CONST_HIGH16: {
                    int literal = ((NarrowLiteralInstruction) instruction).getNarrowLiteral();
                    LocalVariable narrow = regA((OneRegisterInstruction) instruction, LocalVariable.Type.NARROW);
                    if (literal == 0) {
                        // could also be a reference assignment to null - add both
                        workBlock.addInstruction(Const.createNarrow(narrow, 0));
                        workBlock.addInstruction(Const.create(
                                regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE), Const.NULL));
                        return null;
                    } else {
                        return Const.createNarrow(narrow, literal);
                    }
                }
                case CONST_WIDE:
                case CONST_WIDE_16:
                case CONST_WIDE_32:
                case CONST_WIDE_HIGH16: {
                    return Const.createWide(regA((OneRegisterInstruction) instruction, LocalVariable.Type.WIDE),
                                            ((WideLiteralInstruction) instruction).getWideLiteral());
                }
                case CONST_STRING:
                case CONST_STRING_JUMBO: {
                    return Const.createString(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE),
                                              ((StringReference) ((ReferenceInstruction) instruction).getReference())
                                                      .getString());
                }
                case CONST_CLASS: {
                    return Const.createClass(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE),
                                             type((ReferenceInstruction) instruction));
                }
                case MONITOR_ENTER: {
                    return Monitor.createEnter(regA((OneRegisterInstruction) instruction,
                                                    LocalVariable.Type.REFERENCE));
                }
                case MONITOR_EXIT: {
                    return Monitor.createExit(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE));
                }
                case CHECK_CAST: {
                    return CheckCast.create(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE),
                                            type((ReferenceInstruction) instruction));
                }
                case INSTANCE_OF: {
                    return InstanceOf.builder()
                            .target(regA((OneRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .operand(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .type(type((ReferenceInstruction) instruction))
                            .build();
                }
                case ARRAY_LENGTH: {
                    return ArrayLength.builder()
                            .target(regA((OneRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .operand(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .build();
                }
                case NEW_INSTANCE: {
                    // eaten by the constructor invoke-direct call
                    return null;
                }
                case NEW_ARRAY: {
                    return NewArray.lengthBuilder()
                            .target(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .type((ArrayTypeMirror) type((ReferenceInstruction) instruction))
                            .length(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case FILLED_NEW_ARRAY: {
                    Instruction35c c = (Instruction35c) instruction;
                    ArrayTypeMirror type = (ArrayTypeMirror) type(c);
                    List<LocalVariable> contents = new ArrayList<>(c.getRegisterCount());
                    LocalVariable.Type elementType = asmTypeToVariableType(type.getComponentType().getType());
                    if (c.getRegisterCount() > 0) { contents.add(reg(c.getRegisterC(), elementType)); }
                    if (c.getRegisterCount() > 1) { contents.add(reg(c.getRegisterD(), elementType)); }
                    if (c.getRegisterCount() > 2) { contents.add(reg(c.getRegisterE(), elementType)); }
                    if (c.getRegisterCount() > 3) { contents.add(reg(c.getRegisterF(), elementType)); }
                    if (c.getRegisterCount() > 4) { contents.add(reg(c.getRegisterG(), elementType)); }
                    return newArray(type, contents);
                }
                case FILLED_NEW_ARRAY_RANGE: {
                    Instruction3rc c = (Instruction3rc) instruction;
                    ArrayTypeMirror type = (ArrayTypeMirror) type(c);
                    List<LocalVariable> contents = new ArrayList<>(c.getRegisterCount());
                    LocalVariable.Type elementType = asmTypeToVariableType(type.getComponentType().getType());
                    for (int i = 0; i < c.getRegisterCount(); i++) {
                        contents.add(reg(c.getStartRegister() + i, elementType));
                    }
                    return newArray(type, contents);
                }
                case FILL_ARRAY_DATA: {
                    Instruction31t t = (Instruction31t) instruction;
                    ArrayPayload payload = (ArrayPayload) instructions.getInstructionAtOffset(
                            instructions.getOffset(i) + t.getCodeOffset());
                    PrimitiveIterable iterable;
                    switch (payload.getElementWidth()) {
                        case 1: {
                            MutableByteList list = ByteLists.mutable.empty();
                            for (Number number : payload.getArrayElements()) {
                                list.add(number.byteValue());
                            }
                            iterable = list;
                            break;
                        }
                        case 2: {
                            MutableShortList list = ShortLists.mutable.empty();
                            for (Number number : payload.getArrayElements()) {
                                list.add(number.shortValue());
                            }
                            iterable = list;
                            break;
                        }
                        case 4: {
                            MutableIntList list = IntLists.mutable.empty();
                            for (Number number : payload.getArrayElements()) {
                                list.add(number.intValue());
                            }
                            iterable = list;
                            break;
                        }
                        case 8: {
                            MutableLongList list = LongLists.mutable.empty();
                            for (Number number : payload.getArrayElements()) {
                                list.add(number.longValue());
                            }
                            iterable = list;
                            break;
                        }
                        default: {
                            throw new DexVerifyException("Invalid element width");
                        }
                    }
                    return FillArray.create(regA(t, LocalVariable.Type.REFERENCE), iterable);
                }

                case IGET:
                case IGET_BOOLEAN:
                case IGET_BYTE:
                case IGET_CHAR:
                case IGET_SHORT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, false);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.load()
                            .field(field)
                            .instance(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .value(regA((TwoRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case IPUT:
                case IPUT_BOOLEAN:
                case IPUT_BYTE:
                case IPUT_CHAR:
                case IPUT_SHORT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, false);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.store()
                            .field(field)
                            .instance(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .value(regA((TwoRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case IGET_OBJECT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, false);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.load()
                            .field(field)
                            .instance(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .value(regA((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .build();
                }
                case IPUT_OBJECT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, false);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.store()
                            .field(field)
                            .instance(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .value(regA((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .build();
                }
                case IGET_WIDE: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, false);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.load()
                            .field(field)
                            .instance(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .value(regA((TwoRegisterInstruction) instruction, LocalVariable.Type.WIDE))
                            .build();
                }
                case IPUT_WIDE: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, false);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.store()
                            .field(field)
                            .instance(regB((TwoRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .value(regA((TwoRegisterInstruction) instruction, LocalVariable.Type.WIDE))
                            .build();
                }

                case SGET:
                case SGET_BOOLEAN:
                case SGET_BYTE:
                case SGET_CHAR:
                case SGET_SHORT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, true);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.load()
                            .field(field)
                            .value(regA((OneRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case SPUT:
                case SPUT_BOOLEAN:
                case SPUT_BYTE:
                case SPUT_CHAR:
                case SPUT_SHORT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, true);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.store()
                            .field(field)
                            .value(regA((OneRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case SGET_OBJECT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, true);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.load()
                            .field(field)
                            .value(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .build();
                }
                case SPUT_OBJECT: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, true);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.store()
                            .field(field)
                            .value(regA((OneRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .build();
                }
                case SGET_WIDE: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, true);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.load()
                            .field(field)
                            .value(regA((OneRegisterInstruction) instruction, LocalVariable.Type.WIDE))
                            .build();
                }
                case SPUT_WIDE: {
                    FieldMirror field;
                    try {
                        field = field((ReferenceInstruction) instruction, true);
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchFieldError.class);
                        return null;
                    }
                    return LoadStore.store()
                            .field(field)
                            .value(regA((OneRegisterInstruction) instruction, LocalVariable.Type.WIDE))
                            .build();
                }

                case AGET: {
                    return ArrayLoadStore.loadIntFloat()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case AGET_BOOLEAN: {
                    return ArrayLoadStore.loadBoolean()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case AGET_BYTE: {
                    return ArrayLoadStore.loadByte()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case AGET_CHAR: {
                    return ArrayLoadStore.loadChar()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case AGET_SHORT: {
                    return ArrayLoadStore.loadShort()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }

                case APUT: {
                    return ArrayLoadStore.storeIntFloat()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case APUT_BOOLEAN: {
                    return ArrayLoadStore.storeBoolean()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case APUT_BYTE: {
                    return ArrayLoadStore.storeByte()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case APUT_CHAR: {
                    return ArrayLoadStore.storeChar()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case APUT_SHORT: {
                    return ArrayLoadStore.storeShort()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .build();
                }
                case AGET_OBJECT: {
                    return ArrayLoadStore.loadReference()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .build();
                }
                case APUT_OBJECT: {
                    return ArrayLoadStore.storeReference()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .build();
                }
                case AGET_WIDE: {
                    return ArrayLoadStore.loadWide()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.WIDE))
                            .build();
                }
                case APUT_WIDE: {
                    return ArrayLoadStore.storeWide()
                            .array(regB((ThreeRegisterInstruction) instruction, LocalVariable.Type.REFERENCE))
                            .index(regC((ThreeRegisterInstruction) instruction, LocalVariable.Type.NARROW))
                            .value(regA((ThreeRegisterInstruction) instruction, LocalVariable.Type.WIDE))
                            .build();
                }

                case INVOKE_STATIC:
                case INVOKE_DIRECT:
                case INVOKE_INTERFACE:
                case INVOKE_SUPER:
                case INVOKE_VIRTUAL:
                case INVOKE_STATIC_RANGE:
                case INVOKE_DIRECT_RANGE:
                case INVOKE_INTERFACE_RANGE:
                case INVOKE_SUPER_RANGE:
                case INVOKE_VIRTUAL_RANGE: {
                    int regCount = ((VariableRegisterInstruction) instruction).getRegisterCount();
                    int[] regs = new int[regCount];
                    switch (instruction.getOpcode()) {
                        case INVOKE_STATIC:
                        case INVOKE_DIRECT:
                        case INVOKE_INTERFACE:
                        case INVOKE_SUPER:
                        case INVOKE_VIRTUAL: {
                            if (regCount > 0) { regs[0] = ((FiveRegisterInstruction) instruction).getRegisterC(); }
                            if (regCount > 1) { regs[1] = ((FiveRegisterInstruction) instruction).getRegisterD(); }
                            if (regCount > 2) { regs[2] = ((FiveRegisterInstruction) instruction).getRegisterE(); }
                            if (regCount > 3) { regs[3] = ((FiveRegisterInstruction) instruction).getRegisterF(); }
                            if (regCount > 4) { regs[4] = ((FiveRegisterInstruction) instruction).getRegisterG(); }
                            break;
                        }
                        case INVOKE_STATIC_RANGE:
                        case INVOKE_DIRECT_RANGE:
                        case INVOKE_INTERFACE_RANGE:
                        case INVOKE_SUPER_RANGE:
                        case INVOKE_VIRTUAL_RANGE: {
                            for (int j = 0; j < regCount; j++) {
                                regs[j] = ((RegisterRangeInstruction) instruction).getStartRegister() + j;
                            }
                            break;
                        }
                        default:
                            throw new AssertionError();
                    }
                    ReferenceInstruction referenceInstruction = (ReferenceInstruction) instruction;
                    MethodReference reference = (MethodReference) referenceInstruction.getReference();
                    TypeMirror declaringType = classpath.getTypeMirror(Type.getType(reference.getDefiningClass()));
                    Invoke.Type invokeType;
                    switch (instruction.getOpcode()) {
                        case INVOKE_STATIC:
                        case INVOKE_STATIC_RANGE: {
                            invokeType = Invoke.Type.NORMAL;
                            break;
                        }
                        case INVOKE_VIRTUAL:
                        case INVOKE_VIRTUAL_RANGE: {
                            invokeType = Invoke.Type.NORMAL;
                            if (declaringType instanceof ExternalTypeMirror) {
                                ((ExternalTypeMirror) declaringType).setInterface(false);
                            } else {
                                if (declaringType.isInterface()) {
                                    throw new DexVerifyException("invoke-virtual on interface type");
                                }
                            }
                            break;
                        }
                        case INVOKE_INTERFACE:
                        case INVOKE_INTERFACE_RANGE: {
                            invokeType = Invoke.Type.NORMAL;
                            if (declaringType instanceof ExternalTypeMirror) {
                                ((ExternalTypeMirror) declaringType).setInterface(true);
                            } else {
                                if (!declaringType.isInterface()) {
                                    throw new DexVerifyException("invoke-interface on non-interface type");
                                }
                            }
                            break;
                        }
                        case INVOKE_DIRECT:
                        case INVOKE_DIRECT_RANGE:
                        case INVOKE_SUPER:
                        case INVOKE_SUPER_RANGE: {
                            invokeType = Invoke.Type.SPECIAL;
                            break;
                        }
                        default:
                            throw new AssertionError();
                    }
                    LocalVariable returnVariable = null;
                    List<LocalVariable> parameters = new ArrayList<>();
                    Iterator<Type> declaredTypes = Iterators.transform(reference.getParameterTypes().iterator(),
                                                                       s -> {
                                                                           assert s != null;
                                                                           return Type.getType(s.toString());
                                                                       });
                    for (int j = 0; j < regs.length; j++) {
                        int reg = regs[j];
                        if (j == 0 && reference.getName().equals("<init>") &&
                            (instruction.getOpcode() == Opcode.INVOKE_DIRECT ||
                             instruction.getOpcode() == Opcode.INVOKE_DIRECT_RANGE) &&
                            typeChecker.getRegisterInputTypes(i).get(reg) == RegisterType.REFERENCE_UNINITIALIZED) {
                            invokeType = Invoke.Type.NEW_INSTANCE;
                            returnVariable = reg(reg, LocalVariable.Type.REFERENCE);
                            // don't pass in `this`
                            continue;
                        }

                        LocalVariable.Type varType;
                        if (j == 0 &&
                            instruction.getOpcode() != Opcode.INVOKE_STATIC &&
                            instruction.getOpcode() != Opcode.INVOKE_STATIC_RANGE) {
                            varType = LocalVariable.Type.REFERENCE;
                        } else {
                            if (!declaredTypes.hasNext()) {
                                throw new DexVerifyException("Parameter overflow");
                            }
                            varType = asmTypeToVariableType(declaredTypes.next());
                        }

                        parameters.add(reg(reg, varType));
                        if (varType == LocalVariable.Type.WIDE) {
                            if (j == regs.length - 1 || regs[j + 1] != reg + 1) {
                                throw new DexVerifyException("Wide parameter is not a register pair");
                            }
                            j++;
                        }
                    }
                    if (declaredTypes.hasNext()) {
                        throw new DexVerifyException("Parameter underflow");
                    }
                    if (returnVariable == null) {
                        returnVariable = findReturnVariable();
                    }

                    MethodMirror method;
                    try {
                        method = resolveMethod(classpath, reference,
                                               TriState.valueOf(instruction.getOpcode() == Opcode.INVOKE_STATIC ||
                                                                instruction.getOpcode() == Opcode.INVOKE_STATIC_RANGE));
                    } catch (NoSuchMemberException e) {
                        linkageError(e, NoSuchMethodError.class);
                        return null;
                    }

                    workBlock.addInstruction(Invoke.builder()
                                                     .type(invokeType)
                                                     .method(method)
                                                     .parameters(parameters)
                                                     .returnValue(returnVariable)
                                                     .build());
                    if (invokeType == Invoke.Type.NEW_INSTANCE) {
                        LocalVariable src = reg(regs[0], LocalVariable.Type.REFERENCE);
                        typeChecker.getRegisterAliases(i, regs[0]).forEach(alias -> {
                            if (alias != regs[0]) {
                                workBlock.addInstruction(Move.builder()
                                                                 .from(src)
                                                                 .to(reg(alias, LocalVariable.Type.REFERENCE))
                                                                 .build());
                            }
                        });
                    }
                    return null;
                }

                case NEG_INT: {
                    return unaryOp(UnaryOperation.Type.NEGATE_INT, (TwoRegisterInstruction) instruction);
                }
                case NOT_INT: {
                    return unaryOp(UnaryOperation.Type.NOT_INT, (TwoRegisterInstruction) instruction);
                }
                case NEG_LONG: {
                    return unaryOp(UnaryOperation.Type.NEGATE_LONG, (TwoRegisterInstruction) instruction);
                }
                case NOT_LONG: {
                    return unaryOp(UnaryOperation.Type.NOT_LONG, (TwoRegisterInstruction) instruction);
                }
                case NEG_FLOAT: {
                    return unaryOp(UnaryOperation.Type.NEGATE_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case NEG_DOUBLE: {
                    return unaryOp(UnaryOperation.Type.NEGATE_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case INT_TO_LONG: {
                    return unaryOp(UnaryOperation.Type.INT_TO_LONG, (TwoRegisterInstruction) instruction);
                }
                case INT_TO_FLOAT: {
                    return unaryOp(UnaryOperation.Type.INT_TO_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case INT_TO_DOUBLE: {
                    return unaryOp(UnaryOperation.Type.INT_TO_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case LONG_TO_INT: {
                    return unaryOp(UnaryOperation.Type.LONG_TO_INT, (TwoRegisterInstruction) instruction);
                }
                case LONG_TO_FLOAT: {
                    return unaryOp(UnaryOperation.Type.LONG_TO_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case LONG_TO_DOUBLE: {
                    return unaryOp(UnaryOperation.Type.LONG_TO_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case FLOAT_TO_INT: {
                    return unaryOp(UnaryOperation.Type.FLOAT_TO_INT, (TwoRegisterInstruction) instruction);
                }
                case FLOAT_TO_LONG: {
                    return unaryOp(UnaryOperation.Type.FLOAT_TO_LONG, (TwoRegisterInstruction) instruction);
                }
                case FLOAT_TO_DOUBLE: {
                    return unaryOp(UnaryOperation.Type.FLOAT_TO_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case DOUBLE_TO_INT: {
                    return unaryOp(UnaryOperation.Type.DOUBLE_TO_INT, (TwoRegisterInstruction) instruction);
                }
                case DOUBLE_TO_LONG: {
                    return unaryOp(UnaryOperation.Type.DOUBLE_TO_LONG, (TwoRegisterInstruction) instruction);
                }
                case DOUBLE_TO_FLOAT: {
                    return unaryOp(UnaryOperation.Type.DOUBLE_TO_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case INT_TO_BYTE: {
                    return unaryOp(UnaryOperation.Type.INT_TO_BYTE, (TwoRegisterInstruction) instruction);
                }
                case INT_TO_CHAR: {
                    return unaryOp(UnaryOperation.Type.INT_TO_CHAR, (TwoRegisterInstruction) instruction);
                }
                case INT_TO_SHORT: {
                    return unaryOp(UnaryOperation.Type.INT_TO_SHORT, (TwoRegisterInstruction) instruction);
                }

                case CMPL_FLOAT: {
                    return binaryOp(BinaryOperation.Type.COMPARE_FLOAT_BIAS_L, (ThreeRegisterInstruction) instruction);
                }
                case CMPG_FLOAT: {
                    return binaryOp(BinaryOperation.Type.COMPARE_FLOAT_BIAS_G, (ThreeRegisterInstruction) instruction);
                }
                case CMPL_DOUBLE: {
                    return binaryOp(BinaryOperation.Type.COMPARE_DOUBLE_BIAS_L, (ThreeRegisterInstruction) instruction);
                }
                case CMPG_DOUBLE: {
                    return binaryOp(BinaryOperation.Type.COMPARE_DOUBLE_BIAS_G, (ThreeRegisterInstruction) instruction);
                }
                case CMP_LONG: {
                    return binaryOp(BinaryOperation.Type.COMPARE_LONG, (ThreeRegisterInstruction) instruction);
                }
                case ADD_INT: {
                    return binaryOp(BinaryOperation.Type.ADD_INT, (ThreeRegisterInstruction) instruction);
                }
                case SUB_INT: {
                    return binaryOp(BinaryOperation.Type.SUB_INT, (ThreeRegisterInstruction) instruction);
                }
                case MUL_INT: {
                    return binaryOp(BinaryOperation.Type.MUL_INT, (ThreeRegisterInstruction) instruction);
                }
                case DIV_INT: {
                    return binaryOp(BinaryOperation.Type.DIV_INT, (ThreeRegisterInstruction) instruction);
                }
                case REM_INT: {
                    return binaryOp(BinaryOperation.Type.REM_INT, (ThreeRegisterInstruction) instruction);
                }
                case ADD_FLOAT: {
                    return binaryOp(BinaryOperation.Type.ADD_FLOAT, (ThreeRegisterInstruction) instruction);
                }
                case SUB_FLOAT: {
                    return binaryOp(BinaryOperation.Type.SUB_FLOAT, (ThreeRegisterInstruction) instruction);
                }
                case MUL_FLOAT: {
                    return binaryOp(BinaryOperation.Type.MUL_FLOAT, (ThreeRegisterInstruction) instruction);
                }
                case DIV_FLOAT: {
                    return binaryOp(BinaryOperation.Type.DIV_FLOAT, (ThreeRegisterInstruction) instruction);
                }
                case REM_FLOAT: {
                    return binaryOp(BinaryOperation.Type.REM_FLOAT, (ThreeRegisterInstruction) instruction);
                }
                case AND_INT: {
                    return binaryOp(BinaryOperation.Type.AND_INT, (ThreeRegisterInstruction) instruction);
                }
                case OR_INT: {
                    return binaryOp(BinaryOperation.Type.OR_INT, (ThreeRegisterInstruction) instruction);
                }
                case XOR_INT: {
                    return binaryOp(BinaryOperation.Type.XOR_INT, (ThreeRegisterInstruction) instruction);
                }
                case SHL_INT: {
                    return binaryOp(BinaryOperation.Type.SHL_INT, (ThreeRegisterInstruction) instruction);
                }
                case SHR_INT: {
                    return binaryOp(BinaryOperation.Type.SHR_INT, (ThreeRegisterInstruction) instruction);
                }
                case USHR_INT: {
                    return binaryOp(BinaryOperation.Type.USHR_INT, (ThreeRegisterInstruction) instruction);
                }
                case ADD_LONG: {
                    return binaryOp(BinaryOperation.Type.ADD_LONG, (ThreeRegisterInstruction) instruction);
                }
                case SUB_LONG: {
                    return binaryOp(BinaryOperation.Type.SUB_LONG, (ThreeRegisterInstruction) instruction);
                }
                case MUL_LONG: {
                    return binaryOp(BinaryOperation.Type.MUL_LONG, (ThreeRegisterInstruction) instruction);
                }
                case DIV_LONG: {
                    return binaryOp(BinaryOperation.Type.DIV_LONG, (ThreeRegisterInstruction) instruction);
                }
                case REM_LONG: {
                    return binaryOp(BinaryOperation.Type.REM_LONG, (ThreeRegisterInstruction) instruction);
                }
                case ADD_DOUBLE: {
                    return binaryOp(BinaryOperation.Type.ADD_DOUBLE, (ThreeRegisterInstruction) instruction);
                }
                case SUB_DOUBLE: {
                    return binaryOp(BinaryOperation.Type.SUB_DOUBLE, (ThreeRegisterInstruction) instruction);
                }
                case MUL_DOUBLE: {
                    return binaryOp(BinaryOperation.Type.MUL_DOUBLE, (ThreeRegisterInstruction) instruction);
                }
                case DIV_DOUBLE: {
                    return binaryOp(BinaryOperation.Type.DIV_DOUBLE, (ThreeRegisterInstruction) instruction);
                }
                case REM_DOUBLE: {
                    return binaryOp(BinaryOperation.Type.REM_DOUBLE, (ThreeRegisterInstruction) instruction);
                }
                case AND_LONG: {
                    return binaryOp(BinaryOperation.Type.AND_LONG, (ThreeRegisterInstruction) instruction);
                }
                case OR_LONG: {
                    return binaryOp(BinaryOperation.Type.OR_LONG, (ThreeRegisterInstruction) instruction);
                }
                case XOR_LONG: {
                    return binaryOp(BinaryOperation.Type.XOR_LONG, (ThreeRegisterInstruction) instruction);
                }
                case SHL_LONG: {
                    return binaryOp(BinaryOperation.Type.SHL_LONG, (ThreeRegisterInstruction) instruction);
                }
                case SHR_LONG: {
                    return binaryOp(BinaryOperation.Type.SHR_LONG, (ThreeRegisterInstruction) instruction);
                }
                case USHR_LONG: {
                    return binaryOp(BinaryOperation.Type.USHR_LONG, (ThreeRegisterInstruction) instruction);
                }
                case ADD_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.ADD_INT, (TwoRegisterInstruction) instruction);
                }
                case SUB_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SUB_INT, (TwoRegisterInstruction) instruction);
                }
                case MUL_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.MUL_INT, (TwoRegisterInstruction) instruction);
                }
                case DIV_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.DIV_INT, (TwoRegisterInstruction) instruction);
                }
                case REM_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.REM_INT, (TwoRegisterInstruction) instruction);
                }
                case ADD_FLOAT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.ADD_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case SUB_FLOAT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SUB_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case MUL_FLOAT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.MUL_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case DIV_FLOAT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.DIV_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case REM_FLOAT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.REM_FLOAT, (TwoRegisterInstruction) instruction);
                }
                case AND_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.AND_INT, (TwoRegisterInstruction) instruction);
                }
                case OR_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.OR_INT, (TwoRegisterInstruction) instruction);
                }
                case XOR_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.XOR_INT, (TwoRegisterInstruction) instruction);
                }
                case SHL_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SHL_INT, (TwoRegisterInstruction) instruction);
                }
                case SHR_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SHR_INT, (TwoRegisterInstruction) instruction);
                }
                case USHR_INT_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.USHR_INT, (TwoRegisterInstruction) instruction);
                }
                case ADD_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.ADD_LONG, (TwoRegisterInstruction) instruction);
                }
                case SUB_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SUB_LONG, (TwoRegisterInstruction) instruction);
                }
                case MUL_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.MUL_LONG, (TwoRegisterInstruction) instruction);
                }
                case DIV_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.DIV_LONG, (TwoRegisterInstruction) instruction);
                }
                case REM_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.REM_LONG, (TwoRegisterInstruction) instruction);
                }
                case ADD_DOUBLE_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.ADD_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case SUB_DOUBLE_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SUB_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case MUL_DOUBLE_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.MUL_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case DIV_DOUBLE_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.DIV_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case REM_DOUBLE_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.REM_DOUBLE, (TwoRegisterInstruction) instruction);
                }
                case AND_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.AND_LONG, (TwoRegisterInstruction) instruction);
                }
                case OR_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.OR_LONG, (TwoRegisterInstruction) instruction);
                }
                case XOR_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.XOR_LONG, (TwoRegisterInstruction) instruction);
                }
                case SHL_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SHL_LONG, (TwoRegisterInstruction) instruction);
                }
                case SHR_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.SHR_LONG, (TwoRegisterInstruction) instruction);
                }
                case USHR_LONG_2ADDR: {
                    return binaryOpInPlace(BinaryOperation.Type.USHR_LONG, (TwoRegisterInstruction) instruction);
                }
                case ADD_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.ADD, (Instruction22b) instruction);
                }
                case RSUB_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.RSUB, (Instruction22b) instruction);
                }
                case MUL_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.MUL, (Instruction22b) instruction);
                }
                case DIV_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.DIV, (Instruction22b) instruction);
                }
                case REM_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.REM, (Instruction22b) instruction);
                }
                case AND_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.AND, (Instruction22b) instruction);
                }
                case OR_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.OR, (Instruction22b) instruction);
                }
                case XOR_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.XOR, (Instruction22b) instruction);
                }
                case SHL_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.SHL, (Instruction22b) instruction);
                }
                case SHR_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.SHR, (Instruction22b) instruction);
                }
                case USHR_INT_LIT8: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.USHR, (Instruction22b) instruction);
                }
                case ADD_INT_LIT16: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.ADD, (Instruction22s) instruction);
                }
                case RSUB_INT: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.RSUB, (Instruction22s) instruction);
                }
                case MUL_INT_LIT16: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.MUL, (Instruction22s) instruction);
                }
                case DIV_INT_LIT16: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.DIV, (Instruction22s) instruction);
                }
                case REM_INT_LIT16: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.REM, (Instruction22s) instruction);
                }
                case AND_INT_LIT16: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.AND, (Instruction22s) instruction);
                }
                case OR_INT_LIT16: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.OR, (Instruction22s) instruction);
                }
                case XOR_INT_LIT16: {
                    return binaryOpLiteral(LiteralBinaryOperation.Type.XOR, (Instruction22s) instruction);
                }
            }
            throw new DexVerifyException("Unsupported opcode: " + instruction.getOpcode());
        }
    }

    static MethodMirror resolveMethod(Classpath classpath, MethodReference ref, @NonNull TriState isStatic) {
        TypeMirror declaring = classpath.getTypeMirror(Type.getType(ref.getDefiningClass()));
        Type methodType = getMethodType(ref);
        return declaring.method(ref.getName(), methodType, isStatic);
    }

    static FieldMirror resolveField(Classpath classpath, FieldReference ref, @NonNull TriState isStatic) {
        TypeMirror declaring = classpath.getTypeMirror(Type.getType(ref.getDefiningClass()));
        return declaring.field(ref.getName(), Type.getType(ref.getType()), isStatic);
    }

    static Type getMethodType(MethodReference ref) {
        return Type.getMethodType(
                Type.getType(ref.getReturnType()),
                ref.getParameterTypes().stream().map(t -> Type.getType(t.toString())).toArray(Type[]::new));
    }

    @Nullable
    static LocalVariable.Type asmTypeToVariableType(Type type) {
        return TypeChecker.categorizeAsmType(type, LocalVariable.Type.NARROW, LocalVariable.Type.WIDE,
                                             LocalVariable.Type.REFERENCE);
    }

}
