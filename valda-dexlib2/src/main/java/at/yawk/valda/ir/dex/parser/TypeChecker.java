package at.yawk.valda.ir.dex.parser;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.jf.dexlib2.Opcode;
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
import org.jf.dexlib2.iface.reference.MethodReference;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
class TypeChecker {
    private static final int RESULT_REGISTER = -2;

    private final InstructionList instructions;

    private final BitSet dirty;
    private final BitSet blockStarts;
    private final List<Types> inputTypes;

    private final List<Catch> catches = new ArrayList<>();

    private int i;
    private Instruction insn;
    private Types outputTypes;

    TypeChecker(InstructionList instructions) {
        this.instructions = instructions;
        this.dirty = new BitSet(instructions.getInstructionCount());
        this.blockStarts = new BitSet(instructions.getInstructionCount() + 1);
        this.inputTypes = new ArrayList<>(instructions.getInstructionCount());

        dirty.set(0);
        blockStarts.set(0);
        blockStarts.set(instructions.getInstructionCount());
        inputTypes.add(new Types());
    }

    void addParameter(int registerIndex, RegisterType type) {
        inputTypes.set(0, inputTypes.get(0).assign(registerIndex, type));
    }

    void addCatch(int startOffset, int endOffset, int handlerOffset) {
        int start = instructions.getInstructionIndexAtOffset(startOffset);
        int end = instructions.getInstructionIndexAtOffset(endOffset);
        int handler = instructions.getInstructionIndexAtOffset(handlerOffset);
        blockStarts.set(start);
        blockStarts.set(end);
        catches.add(new Catch(start, end, handler));
    }

    @SuppressWarnings("NewMethodNamingConvention")
    void run() {
        //noinspection StatementWithEmptyBody
        while (visit()) {}
    }

    public boolean isReachable(int instructionIndex) {
        return inputTypes.size() > instructionIndex && inputTypes.get(instructionIndex) != null;
    }

    public IntObjectMap<RegisterType> getRegisterInputTypes(int instructionIndex) {
        checkReachable(instructionIndex);
        return inputTypes.get(instructionIndex).types;
    }

    public IntSet getRegisterAliases(int instructionIndex, int register) {
        checkReachable(instructionIndex);
        IntSet aliases = inputTypes.get(instructionIndex).aliases.get(register);
        return aliases == null ? IntSets.immutable.of(register) : aliases;
    }

    private void checkReachable(int instructionIndex) {
        if (!isReachable(instructionIndex)) {
            throw new IllegalArgumentException(
                    "Instruction " + instructions.getInstruction(instructionIndex) + " at index " + instructionIndex +
                    " is not reachable");
        }
    }

    public Iterable<BlockBounds> getBlocks() {
        return () -> new Iterator<BlockBounds>() {
            int start = 0;

            @Override
            public boolean hasNext() {
                return start < instructions.getInstructionCount();
            }

            @Override
            public BlockBounds next() {
                if (!hasNext()) { throw new NoSuchElementException(); }
                int start = this.start;
                int end = blockStarts.nextSetBit(start + 1);
                this.start = end;
                return new BlockBounds(start, end - start);
            }
        };
    }

    private boolean visit() {
        i = dirty.nextSetBit(0);
        if (i == -1) { return false; }
        dirty.clear(i);
        insn = instructions.getInstruction(i);
        if (insn.getOpcode().canContinue()) {
            outputTypes = inputTypes.get(i);
        } else {
            outputTypes = null;
        }

        visitOpcode(insn.getOpcode());
        if (insn.getOpcode().canThrow()) {
            for (Catch c : catches) {
                if (c.start <= i && c.end > i) {
                    blockStarts.set(c.handler);
                    intersectInputTypes(c.handler, inputTypes.get(i));
                }
            }
        }
        if (insn.getOpcode().canContinue()) {
            intersectInputTypes(i + 1, outputTypes);
        }

        return true;
    }

    @VisibleForTesting
    void visitOpcode(Opcode opcode) {
        switch (opcode) {
            case MOVE:
            case MOVE_16:
            case MOVE_FROM16: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.NARROW);
                aliasAB(false);
                break;
            }
            case MOVE_WIDE:
            case MOVE_WIDE_16:
            case MOVE_WIDE_FROM16: {
                setTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.WIDE_PAIR);
                aliasAB(true);
                break;
            }
            case MOVE_OBJECT:
            case MOVE_OBJECT_16:
            case MOVE_OBJECT_FROM16: {
                moveObject();
                break;
            }
            case MOVE_RESULT: {
                expectType(RESULT_REGISTER, RegisterType.NARROW);
                setTypeA(RegisterType.NARROW);
                break;
            }
            case MOVE_RESULT_WIDE: {
                expectType(RESULT_REGISTER, RegisterType.WIDE_PAIR);
                setTypeA(RegisterType.WIDE_PAIR);
                break;
            }
            case MOVE_RESULT_OBJECT: {
                expectType(RESULT_REGISTER, RegisterType.REFERENCE);
                setTypeA(RegisterType.REFERENCE);
                break;
            }
            case MOVE_EXCEPTION: {
                setTypeA(RegisterType.REFERENCE);
                break;
            }
            case CONST:
            case CONST_4:
            case CONST_16:
            case CONST_HIGH16: {
                visitNarrowConst();
                break;
            }
            case CONST_WIDE:
            case CONST_WIDE_32:
            case CONST_WIDE_16:
            case CONST_WIDE_HIGH16: {
                setTypeA(RegisterType.WIDE_PAIR);
                break;
            }
            case CONST_CLASS:
            case CONST_STRING:
            case CONST_STRING_JUMBO: {
                setTypeA(RegisterType.REFERENCE);
                break;
            }
            case INSTANCE_OF:
            case ARRAY_LENGTH: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.REFERENCE);
                break;
            }
            case FILLED_NEW_ARRAY:
            case FILLED_NEW_ARRAY_RANGE: {
                setType(RESULT_REGISTER, RegisterType.REFERENCE);
                break;
            }
            case CMPL_FLOAT:
            case CMPG_FLOAT: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.NARROW);
                break;
            }
            case CMPL_DOUBLE:
            case CMPG_DOUBLE:
            case CMP_LONG: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.WIDE_PAIR);
                break;
            }
            case IGET:
            case IGET_BOOLEAN:
            case IGET_BYTE:
            case IGET_SHORT:
            case IGET_CHAR: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.REFERENCE);
                break;
            }
            case IGET_WIDE: {
                setTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.REFERENCE);
                break;
            }
            case IGET_OBJECT: {
                setTypeA(RegisterType.REFERENCE);
                expectTypeB(RegisterType.REFERENCE);
                break;
            }
            case AGET:
            case AGET_BOOLEAN:
            case AGET_BYTE:
            case AGET_SHORT:
            case AGET_CHAR: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.REFERENCE);
                expectTypeC(RegisterType.NARROW);
                break;
            }
            case AGET_WIDE: {
                setTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.REFERENCE);
                expectTypeC(RegisterType.NARROW);
                break;
            }
            case AGET_OBJECT: {
                setTypeA(RegisterType.REFERENCE);
                expectTypeB(RegisterType.REFERENCE);
                expectTypeC(RegisterType.NARROW);
                break;
            }

            case SGET:
            case SGET_BOOLEAN:
            case SGET_BYTE:
            case SGET_CHAR:
            case SGET_SHORT: {
                setTypeA(RegisterType.NARROW);
                break;
            }
            case SGET_WIDE: {
                setTypeA(RegisterType.WIDE_PAIR);
                break;
            }
            case SGET_OBJECT: {
                setTypeA(RegisterType.REFERENCE);
                break;
            }

            case NEG_INT:
            case NEG_FLOAT:
            case NOT_INT:
            case INT_TO_FLOAT:
            case FLOAT_TO_INT:
            case INT_TO_BYTE:
            case INT_TO_CHAR:
            case INT_TO_SHORT: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.NARROW);
                break;
            }
            case NOT_LONG:
            case NEG_LONG:
            case NEG_DOUBLE:
            case LONG_TO_DOUBLE:
            case DOUBLE_TO_LONG: {
                setTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.WIDE_PAIR);
                break;
            }
            case LONG_TO_FLOAT:
            case LONG_TO_INT:
            case DOUBLE_TO_FLOAT:
            case DOUBLE_TO_INT: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.WIDE_PAIR);
                break;
            }
            case FLOAT_TO_LONG:
            case INT_TO_LONG:
            case FLOAT_TO_DOUBLE:
            case INT_TO_DOUBLE: {
                setTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.NARROW);
                break;
            }
            case ADD_INT:
            case SUB_INT:
            case MUL_INT:
            case DIV_INT:
            case REM_INT:
            case AND_INT:
            case OR_INT:
            case XOR_INT:
            case SHL_INT:
            case SHR_INT:
            case USHR_INT:
            case ADD_FLOAT:
            case SUB_FLOAT:
            case MUL_FLOAT:
            case DIV_FLOAT:
            case REM_FLOAT: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.NARROW);
                expectTypeC(RegisterType.NARROW);
                break;
            }
            case ADD_LONG:
            case SUB_LONG:
            case MUL_LONG:
            case DIV_LONG:
            case REM_LONG:
            case AND_LONG:
            case OR_LONG:
            case XOR_LONG:
            case ADD_DOUBLE:
            case SUB_DOUBLE:
            case MUL_DOUBLE:
            case DIV_DOUBLE:
            case REM_DOUBLE: {
                setTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.WIDE_PAIR);
                expectTypeC(RegisterType.WIDE_PAIR);
                break;
            }
            case SHL_LONG:
            case SHR_LONG:
            case USHR_LONG: {
                setTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.WIDE_PAIR);
                expectTypeC(RegisterType.NARROW);
                break;
            }
            case ADD_INT_2ADDR:
            case SUB_INT_2ADDR:
            case MUL_INT_2ADDR:
            case DIV_INT_2ADDR:
            case REM_INT_2ADDR:
            case AND_INT_2ADDR:
            case OR_INT_2ADDR:
            case XOR_INT_2ADDR:
            case SHL_INT_2ADDR:
            case SHR_INT_2ADDR:
            case USHR_INT_2ADDR:
            case ADD_FLOAT_2ADDR:
            case SUB_FLOAT_2ADDR:
            case MUL_FLOAT_2ADDR:
            case DIV_FLOAT_2ADDR:
            case REM_FLOAT_2ADDR: {
                // need to explicitly set in case this is ZERO.
                setTypeA(RegisterType.NARROW);
                expectTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.NARROW);
                break;
            }
            case ADD_LONG_2ADDR:
            case SUB_LONG_2ADDR:
            case MUL_LONG_2ADDR:
            case DIV_LONG_2ADDR:
            case REM_LONG_2ADDR:
            case AND_LONG_2ADDR:
            case OR_LONG_2ADDR:
            case XOR_LONG_2ADDR:
            case ADD_DOUBLE_2ADDR:
            case SUB_DOUBLE_2ADDR:
            case MUL_DOUBLE_2ADDR:
            case DIV_DOUBLE_2ADDR:
            case REM_DOUBLE_2ADDR: {
                expectTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.WIDE_PAIR);
                break;
            }
            case SHL_LONG_2ADDR:
            case SHR_LONG_2ADDR:
            case USHR_LONG_2ADDR: {
                expectTypeA(RegisterType.WIDE_PAIR);
                expectTypeB(RegisterType.NARROW);
                break;
            }
            case ADD_INT_LIT8:
            case ADD_INT_LIT16:
            case RSUB_INT_LIT8:
            case RSUB_INT:
            case MUL_INT_LIT8:
            case MUL_INT_LIT16:
            case DIV_INT_LIT8:
            case DIV_INT_LIT16:
            case REM_INT_LIT8:
            case REM_INT_LIT16:
            case AND_INT_LIT8:
            case AND_INT_LIT16:
            case OR_INT_LIT8:
            case OR_INT_LIT16:
            case XOR_INT_LIT8:
            case XOR_INT_LIT16:
            case SHL_INT_LIT8:
            case SHR_INT_LIT8:
            case USHR_INT_LIT8: {
                setTypeA(RegisterType.NARROW);
                expectTypeB(RegisterType.NARROW);
                break;
            }

            case NEW_INSTANCE: {
                setTypeA(RegisterType.REFERENCE_UNINITIALIZED);
                break;
            }
            case NEW_ARRAY: {
                setTypeA(RegisterType.REFERENCE);
                expectTypeB(RegisterType.NARROW);
                break;
            }

            case INVOKE_DIRECT:
            case INVOKE_VIRTUAL:
            case INVOKE_SUPER:
            case INVOKE_INTERFACE:
            case INVOKE_DIRECT_RANGE:
            case INVOKE_VIRTUAL_RANGE:
            case INVOKE_SUPER_RANGE:
            case INVOKE_INTERFACE_RANGE: {
                visitMethod(true);
                break;
            }
            case INVOKE_STATIC:
            case INVOKE_STATIC_RANGE: {
                visitMethod(false);
                break;
            }
            case GOTO:
            case GOTO_16:
            case GOTO_32:
            case IF_EQ:
            case IF_NE:
            case IF_LT:
            case IF_GE:
            case IF_GT:
            case IF_LE:
            case IF_EQZ:
            case IF_NEZ:
            case IF_LTZ:
            case IF_GEZ:
            case IF_GTZ:
            case IF_LEZ: {
                branch();
                blockStarts.set(i + 1);
                break;
            }
            case SPARSE_SWITCH:
            case PACKED_SWITCH: {
                branchSwitch();
                blockStarts.set(i + 1);
                break;
            }

            default: {
                if (opcode != Opcode.CHECK_CAST) {
                    if (opcode.setsRegister() || opcode.setsResult()) {
                        throw new AssertionError();
                    }
                }
                break;
            }
        }
        if (!opcode.canContinue()) {
            blockStarts.set(i + 1);
        }
    }

    @VisibleForTesting
    void moveObject() {
        RegisterType registerType = inputTypes.get(i).types.get(((TwoRegisterInstruction) insn).getRegisterB());
        if (registerType == RegisterType.REFERENCE_UNINITIALIZED) {
            expectTypeB(RegisterType.REFERENCE_UNINITIALIZED);
            setTypeA(RegisterType.REFERENCE_UNINITIALIZED);
        } else {
            expectTypeB(RegisterType.REFERENCE);
            setTypeA(RegisterType.REFERENCE);
        }
        aliasAB(false);
    }

    @VisibleForTesting
    void visitNarrowConst() {
        if (((NarrowLiteralInstruction) insn).getNarrowLiteral() == 0) {
            setTypeA(RegisterType.ZERO);
        } else {
            setTypeA(RegisterType.NARROW);
        }
    }

    @VisibleForTesting
    void setTypeA(@Nullable RegisterType type) {
        setType(((OneRegisterInstruction) insn).getRegisterA(), type);
    }

    @VisibleForTesting
    void expectTypeA(@NonNull RegisterType type) {
        expectType(((TwoRegisterInstruction) insn).getRegisterA(), type);
    }

    @VisibleForTesting
    void expectTypeB(@NonNull RegisterType type) {
        expectType(((TwoRegisterInstruction) insn).getRegisterB(), type);
    }

    @VisibleForTesting
    void expectTypeC(@NonNull RegisterType type) {
        expectType(((ThreeRegisterInstruction) insn).getRegisterC(), type);
    }

    @VisibleForTesting
    void setType(int reg, @Nullable RegisterType type) {
        if (!insn.getOpcode().canContinue()) { throw new IllegalStateException(); }
        outputTypes = outputTypes.assign(reg, type);
    }

    @VisibleForTesting
    void expectType(int reg, @NonNull RegisterType type) {
        if (type == RegisterType.WIDE_PAIR) {
            expectType(reg, RegisterType.WIDE_LOW);
            expectType(reg + 1, RegisterType.WIDE_HIGH);
            return;
        }

        RegisterType actual = inputTypes.get(i).types.get(reg);
        if (!typesCompatible(type, actual)) {
            throw new DexVerifyException(
                    "Invalid input type for instruction " + insn + " at index " + i + ": expected " + type +
                    " but was " + actual);
        }
    }

    @VisibleForTesting
    void aliasAB(boolean wide) {
        outputTypes = outputTypes.alias(((TwoRegisterInstruction) insn).getRegisterA(),
                                        ((TwoRegisterInstruction) insn).getRegisterB());
        if (wide) {
            outputTypes = outputTypes.alias(((TwoRegisterInstruction) insn).getRegisterA() + 1,
                                            ((TwoRegisterInstruction) insn).getRegisterB() + 1);
        }
    }

    private static boolean typesCompatible(RegisterType dest, RegisterType src) {
        return dest == src ||
               (src == RegisterType.ZERO && (dest == RegisterType.NARROW || dest == RegisterType.REFERENCE));
    }

    @VisibleForTesting
    void visitMethod(boolean instanceMethod) {
        MethodReference ref = (MethodReference) ((ReferenceInstruction) insn).getReference();

        List<RegisterType> expectedTypes = new ArrayList<>();
        if (instanceMethod) {
            expectedTypes.add(RegisterType.REFERENCE);
        }
        for (CharSequence sequence : ref.getParameterTypes()) {
            RegisterType registerType = asmTypeToRegisterType(Type.getType(sequence.toString()));
            if (registerType == RegisterType.WIDE_PAIR) {
                expectedTypes.add(RegisterType.WIDE_LOW);
                expectedTypes.add(RegisterType.WIDE_HIGH);
            } else {
                expectedTypes.add(registerType);
            }
        }
        int[] paramRegisters = new int[expectedTypes.size()];
        if (((VariableRegisterInstruction) insn).getRegisterCount() != paramRegisters.length) {
            throw new DexVerifyException("Mismatched parameter count");
        }
        if (insn instanceof FiveRegisterInstruction) {
            if (paramRegisters.length > 0) { paramRegisters[0] = ((FiveRegisterInstruction) insn).getRegisterC(); }
            if (paramRegisters.length > 1) { paramRegisters[1] = ((FiveRegisterInstruction) insn).getRegisterD(); }
            if (paramRegisters.length > 2) { paramRegisters[2] = ((FiveRegisterInstruction) insn).getRegisterE(); }
            if (paramRegisters.length > 3) { paramRegisters[3] = ((FiveRegisterInstruction) insn).getRegisterF(); }
            if (paramRegisters.length > 4) { paramRegisters[4] = ((FiveRegisterInstruction) insn).getRegisterG(); }
        } else if (insn instanceof RegisterRangeInstruction) {
            for (int j = 0; j < paramRegisters.length; j++) {
                paramRegisters[j] = ((RegisterRangeInstruction) insn).getStartRegister() + j;
            }
        } else {
            throw new AssertionError();
        }
        Types inputTypes = this.inputTypes.get(i);
        for (int j = 0; j < paramRegisters.length; j++) {
            RegisterType expectedType = expectedTypes.get(j);
            RegisterType actualType = inputTypes.types.get(paramRegisters[j]);
            if (!typesCompatible(expectedType, actualType)) {
                if (expectedType == RegisterType.REFERENCE && actualType == RegisterType.REFERENCE_UNINITIALIZED &&
                    j == 0) {
                    // constructor invocation
                    int initialized = paramRegisters[0];
                    IntSet aliases = inputTypes.aliases.get(initialized);
                    if (aliases == null) {
                        setType(initialized, RegisterType.REFERENCE);
                    } else {
                        aliases.forEach(reg -> setType(reg, RegisterType.REFERENCE));
                    }
                } else {
                    throw new DexVerifyException(
                            "Mismatched parameter type: expected " + expectedType + " but was " + actualType);
                }
            }
        }

        setType(RESULT_REGISTER, asmTypeToRegisterType(Type.getType(ref.getReturnType())));
    }

    @Nullable
    static RegisterType asmTypeToRegisterType(Type type) {
        return categorizeAsmType(type, RegisterType.NARROW, RegisterType.WIDE_PAIR, RegisterType.REFERENCE);
    }

    @Nullable
    static <T> T categorizeAsmType(Type type, T narrow, T wide, T ref) {
        switch (type.getSort()) {
            case Type.METHOD: {
                throw new AssertionError();
            }
            case Type.ARRAY:
            case Type.OBJECT: {
                return ref;
            }
            case Type.VOID: {
                return null;
            }
            case Type.INT:
            case Type.FLOAT:
            case Type.SHORT:
            case Type.CHAR:
            case Type.BYTE:
            case Type.BOOLEAN: {
                return narrow;
            }
            case Type.LONG:
            case Type.DOUBLE: {
                return wide;
            }
            default: {
                throw new AssertionError();
            }
        }
    }

    @VisibleForTesting
    void branch() {
        branchBy(((OffsetInstruction) insn).getCodeOffset());
    }

    @VisibleForTesting
    void branchSwitch() {
        int payloadOffset = ((OffsetInstruction) insn).getCodeOffset();
        SwitchPayload payload = (SwitchPayload) instructions.getInstructionAtOffset(
                instructions.getOffset(i) + payloadOffset);
        for (SwitchElement element : payload.getSwitchElements()) {
            branchBy(element.getOffset());
        }
    }

    private void branchBy(int offset) {
        int pos = instructions.getOffset(i) + offset;
        int branchIndex = instructions.getInstructionIndexAtOffset(pos);
        blockStarts.set(branchIndex);
        intersectInputTypes(branchIndex, inputTypes.get(i));
    }

    private void intersectInputTypes(int ix, Types types) {
        if (ix >= inputTypes.size()) {
            while (ix > inputTypes.size()) { inputTypes.add(null); }
            inputTypes.add(types);
            dirty.set(ix);
            return;
        }

        Types old = inputTypes.get(ix);
        if (old == null) {
            inputTypes.set(ix, types);
            dirty.set(ix);
            return;
        }

        Types new_ = old.intersect(types);
        if (!old.equals(new_)) {
            inputTypes.set(ix, new_);
            dirty.set(ix);
        }
    }

    @Value
    private static class Catch {
        private final int start;
        private final int end;
        private final int handler;
    }

    @Value
    @RequiredArgsConstructor
    private static class Types {
        private final IntObjectMap<RegisterType> types;
        private final IntObjectMap<IntSet> aliases;

        Types() {
            this.types = IntObjectMaps.immutable.empty();
            this.aliases = IntObjectMaps.immutable.empty();
        }

        Types assign(int reg, @Nullable RegisterType type) {
            if (type == RegisterType.WIDE_PAIR) {
                return this.assign(reg, RegisterType.WIDE_LOW)
                        .assign(reg + 1, RegisterType.WIDE_HIGH);
            }

            ImmutableIntObjectMap<RegisterType> newTypes =
                    type == null ? types.toImmutable().newWithoutKey(reg) :
                            types.toImmutable().newWithKeyValue(reg, type);
            MutableIntObjectMap<IntSet> newAliases = IntObjectMaps.mutable.empty();
            aliases.forEachKeyValue((other, a) -> {
                if (other != reg) {
                    ImmutableIntSet newA = a.toImmutable().newWithout(reg);
                    if (newA.size() > 1) {
                        newAliases.put(other, newA);
                    }
                }
            });
            return new Types(newTypes, newAliases.asUnmodifiable());
        }

        Types alias(int to, int from) {
            MutableIntObjectMap<IntSet> newAliases = IntObjectMaps.mutable.ofAll(aliases);
            assert aliases.get(to) == null : "should be preceded by setType on `to` (r" + to + ")";
            IntSet oldAliasesFrom = newAliases.get(from);
            MutableIntSet newAliasesFrom =
                    oldAliasesFrom == null ? IntSets.mutable.of(from) : IntSets.mutable.ofAll(oldAliasesFrom);
            newAliasesFrom.add(to);
            newAliasesFrom.forEach(i -> newAliases.put(i, newAliasesFrom));
            return new Types(types, newAliases.asUnmodifiable());
        }

        Types intersect(Types other) {
            MutableIntObjectMap<RegisterType> newTypes = IntObjectMaps.mutable.empty();
            this.types.forEachKeyValue((k, a) -> {
                RegisterType b = other.types.get(k);
                if (b == null) { return; }

                if (typesCompatible(a, b)) {
                    newTypes.put(k, a);
                } else if (typesCompatible(b, a)) {
                    newTypes.put(k, b);
                }
            });
            MutableIntObjectMap<IntSet> newAliases = IntObjectMaps.mutable.empty();
            this.aliases.forEachKeyValue((k, a) -> {
                IntSet b = other.aliases.get(k);
                if (b == null) { return; }

                IntSet intersection = a.select(b::contains);
                if (intersection.size() > 1) {
                    newAliases.put(k, intersection);
                }
            });
            return new Types(newTypes.asUnmodifiable(), newAliases.asUnmodifiable());
        }
    }
}
