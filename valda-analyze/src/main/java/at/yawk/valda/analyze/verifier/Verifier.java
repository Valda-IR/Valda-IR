package at.yawk.valda.analyze.verifier;

import at.yawk.valda.analyze.Analyzer;
import at.yawk.valda.analyze.ExecutionContext;
import at.yawk.valda.analyze.ExecutionResult;
import at.yawk.valda.analyze.FastUnorderedStateCollector;
import at.yawk.valda.analyze.InstructionNode;
import at.yawk.valda.analyze.InterpreterAdapter;
import at.yawk.valda.analyze.StateCollector;
import at.yawk.valda.ir.ArrayTypeMirror;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeMirrors;
import at.yawk.valda.ir.Types;
import at.yawk.valda.ir.code.ArrayLoadStore;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.BlockReference;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.Instructions;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.Monitor;
import at.yawk.valda.ir.code.Try;
import at.yawk.valda.ir.code.UnaryOperation;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.eclipse.collections.api.BooleanIterable;
import org.eclipse.collections.api.ByteIterable;
import org.eclipse.collections.api.CharIterable;
import org.eclipse.collections.api.DoubleIterable;
import org.eclipse.collections.api.FloatIterable;
import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.LongIterable;
import org.eclipse.collections.api.PrimitiveIterable;
import org.eclipse.collections.api.ShortIterable;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
public final class Verifier extends InterpreterAdapter<State> {
    @NonNull private final Classpath classpath;
    @NonNull private final LocalMethodMirror methodMirror;

    private ExecutionContext<State> context;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Analyzer<Object> declaredVariableAnalyzer = findDeclaredVariables();

    @Override
    public void handleException(Exception e) {
        if (e instanceof DexVerifyException) { throw (DexVerifyException) e; }
    }

    private Analyzer<Object> findDeclaredVariables() {
        Analyzer<Object> analyzer = new Analyzer<>(new InterpreterAdapter<Object>() {
            private final Object sentinel = new Object();

            @Override
            protected Object defaultValue() {
                return sentinel;
            }

            @Override
            public <K> StateCollector<K, Object> createStateCollector() {
                return new FastUnorderedStateCollector<>((m1, m2) -> {
                    // intersect key sets
                    Map<LocalVariable, Object> merged = new HashMap<>(m1);
                    merged.keySet().removeIf(k -> !m2.containsKey(k));
                    return merged;
                });
            }

            @Override
            protected Object getParameterValue(@NonNull LocalVariable variable) {
                return new Object();
            }
        });
        assert methodMirror != null;
        MethodBody body = methodMirror.getBody();
        assert body != null;
        analyzer.interpret(body);
        return analyzer;
    }

    private String contextString() {
        //noinspection ObjectToString
        return "[" + context.getBlock() + ":" + context.getIndexInBlock() + " (" + context.getInstruction() + ")]";
    }

    @Override
    public Map<LocalVariable, State> getParameterValues(MethodBody body) {
        List<Type> argumentTypes = TypeMirrors.getEffectiveParameterTypes(methodMirror);
        assert methodMirror.getBody() != null;
        List<LocalVariable> parameterVariables = methodMirror.getBody().getParameters();
        Map<LocalVariable, State> out = new HashMap<>();
        for (int i = 0; i < parameterVariables.size(); i++) {
            State state = new State.OfType(argumentTypes.get(i));
            if (i == 0 && methodMirror.isConstructor()) {
                state = State.UNINITIALIZED_THIS;
            }
            out.put(parameterVariables.get(i), state);
        }
        return out;
    }

    @Nullable
    @Override
    protected InstructionThrow maybeThrow(Instruction instruction) {
        return Instructions.canThrow(instruction) ?
                new InstructionThrow(null, new State.OfType(Types.THROWABLE)) :
                null;
    }

    @Override
    public <K> StateCollector<K, State> createStateCollector() {
        StateCollector<K, State> stateCollector = super.createStateCollector();
        ((FastUnorderedStateCollector) stateCollector).setMergePrevious(true);
        return stateCollector;
    }

    private void collectDefinedLocals(
            Set<LocalVariable> locals,
            Set<BasicBlock> visitedBlocks,
            BasicBlock block
    ) {
        collectDefinedLocals(locals, visitedBlocks, block, block.getInstructions().size());
    }

    /**
     * Collect all locals defined in this block.
     *
     * @param endIndex The index until which instruction locals should be collected (exclusive).
     */
    private void collectDefinedLocals(
            Set<LocalVariable> locals,
            Set<BasicBlock> visitedBlocks,
            BasicBlock block,
            int endIndex
    ) {
        if (!visitedBlocks.add(block)) { return; }

        for (int i = 0; i < endIndex; i++) {
            Instruction insn = block.getInstructions().get(i);
            locals.addAll(insn.getOutputVariables());
        }

        for (BlockReference reference : block.getReferences()) {
            if (reference instanceof BlockReference.Instruction) {
                collectDefinedLocals(locals, visitedBlocks,
                                     ((BlockReference.Instruction) reference).getInstruction().getBlock());
            } else if (reference instanceof BlockReference.CatchHandler) {
                for (BasicBlock enclosed : ((BlockReference.CatchHandler) reference).getCatch()
                        .getTry().getEnclosedBlocks()) {
                    collectDefinedLocals(locals, visitedBlocks, enclosed);
                }
            } else if (reference instanceof BlockReference.EntryPoint) {
                locals.addAll(((BlockReference.EntryPoint) reference).getBody().getParameters());
            } else {
                throw new AssertionError(reference);
            }
        }
    }

    private Set<LocalVariable> getSpecialConstructorInvokeVars(BasicBlock block, int indexInBlock) {
        // for constructor invokes we possibly need to clear the "uninitialized" status of all reference registers.
        InstructionNode<Object> node = getDeclaredVariableAnalyzer().getNodes(block).get(indexInBlock);
        Set<LocalVariable> locals = new HashSet<>(
                node.getInput().stream()
                        .map(Map::keySet)
                        .reduce(Sets::intersection)
                        .orElse(Collections.emptySet())
        );
        locals.removeIf(lv -> lv.getType() != LocalVariable.Type.REFERENCE);
        return locals;
    }

    @Override
    public Set<LocalVariable> getInputVariables(BasicBlock block, int indexInBlock) {
        Instruction instruction = block.getInstructions().get(indexInBlock);
        if (instruction instanceof Invoke && Instructions.isSpecialConstructorInvoke((Invoke) instruction)) {
            Set<LocalVariable> locals = getSpecialConstructorInvokeVars(block, indexInBlock);
            locals.addAll(instruction.getInputVariables());
            return locals;
        }
        return super.getInputVariables(block, indexInBlock);
    }

    @Override
    public Set<LocalVariable> getOutputVariables(BasicBlock block, int indexInBlock) {
        Instruction instruction = block.getInstructions().get(indexInBlock);
        if (instruction instanceof CheckCast) {
            return ImmutableSet.of(((CheckCast) instruction).getVariable());
        }
        if (instruction instanceof Invoke && Instructions.isSpecialConstructorInvoke((Invoke) instruction)) {
            return getSpecialConstructorInvokeVars(block, indexInBlock);
        }
        return super.getOutputVariables(block, indexInBlock);
    }

    @NonNull
    @Override
    protected State merge(LocalVariable variable, @NonNull State left, @NonNull State right) {
        return StateMerger.merge(variable, left, right);
    }

    private boolean hasProperty(State state, State.Property expectedProperty) {
        return state.hasProperty(expectedProperty);
    }

    private void expect(State state, State.Property expectedProperty) {
        if (!hasProperty(state, expectedProperty)) {
            throw new DexVerifyException(
                    contextString() + " State " + state + " does not have property " + expectedProperty);
        }
    }

    private void expectAssignableTo(State state, Type type) {
        expectAssignableTo(state, TypeSet.create(type));
    }

    private void expectAssignableTo(State state, TypeSet types) {
        if (types.matchesTri(type -> state.isAssignableTo(classpath, type)) == TriState.FALSE) {
            throw new DexVerifyException(contextString() + " State " + state + " is not assignable to " + types);
        }
    }

    private void expectAssignableTo(State state, TypeMirror type) {
        expectAssignableTo(state, type, false);
    }

    private void expectAssignableTo(State state, TypeMirror type, boolean allowUninitialized) {
        if (allowUninitialized &&
            state.equals(State.UNINITIALIZED_THIS) && type.equals(methodMirror.getDeclaringType())) {
            return;
        }
        expectAssignableTo(state, type.getType());
    }

    private State cast(State input, Type type) {
        if (input instanceof State.OfType) {
            TypeMirror mirror = classpath.getTypeMirror(type);
            TypeSet filtered = ((State.OfType) input).getTypes().mapNullable(t -> {
                TypeMirror other = classpath.getTypeMirror(t);
                if (TypeMirrors.isSupertype(other, mirror) == TriState.FALSE &&
                    TypeMirrors.isSupertype(mirror, other) == TriState.FALSE) {
                    // the 'other' type is completely disjunct. remove it. Will error at runtime, unless the variable
                    // is null.
                    return null;
                } else {
                    return t;
                }
            });
            if (filtered != null) {
                return new State.OfType(TypeSet.intersect(filtered, TypeSet.create(type)));
            }
        }
        return new State.OfType(type);
    }

    @NonNull
    @Override
    public Iterable<ExecutionResult<State>> execute(@NonNull ExecutionContext<State> context) {
        this.context = context;
        Iterable<ExecutionResult<State>> results = super.execute(context);
        Instruction instruction = context.getInstruction();
        if (instruction instanceof CheckCast) {
            State input = context.getInput(((CheckCast) instruction).getVariable());
            State output = cast(input, ((CheckCast) instruction).getType().getType());
            if (input.equals(output)) {
                // save memory
                output = input;
            } else {
                output.markParent(input);
            }
            State finalOutput = output;
            results = Iterables.transform(results, res -> {
                if (res instanceof ExecutionResult.Continue) {
                    return ExecutionResult.Continue.<State>builder().outputVariables(
                            ImmutableMap.<LocalVariable, State>builder()
                                    .putAll(((ExecutionResult.Continue<State>) res).getOutputVariables())
                                    .put(((CheckCast) instruction).getVariable(), finalOutput)
                                    .build()
                    ).build();
                } else {
                    return res;
                }
            });
        }
        if (instruction instanceof Invoke && Instructions.isSpecialConstructorInvoke((Invoke) instruction)) {

            LocalVariable thisVariable = ((Invoke) instruction).getParameters().get(0);
            State initializedState = new State.OfType(methodMirror.getDeclaringType().getType());
            Map<LocalVariable, State> updated = new HashMap<>();
            for (Map.Entry<LocalVariable, State> entry : context.getInputVariables().entrySet()) {
                if (entry.getKey().getType() != LocalVariable.Type.REFERENCE) {
                    // some input vars might still be non-ref, if they need to be checked for constructor call
                    // parameter compatibility. Ignore those for the output.
                    continue;
                }

                boolean uninitialized = entry.getValue().equals(State.UNINITIALIZED_THIS);
                if (uninitialized) {
                    updated.put(entry.getKey(), initializedState);
                } else {
                    // first parameter must be uninitialized
                    if (entry.getKey().equals(thisVariable)) {
                        throw new DexVerifyException(
                                "Cannot call constructor on variable of state " + entry.getValue());
                    }
                    updated.put(entry.getKey(), entry.getValue());
                }
            }
            results = Iterables.transform(results, res -> {
                if (res instanceof ExecutionResult.Continue) {
                    return ExecutionResult.Continue.<State>builder().outputVariables(updated).build();
                } else {
                    return res;
                }
            });
        }
        // cast exceptions to the proper types for their catch clauses
        results = Iterables.transform(results, res -> {
            if (res instanceof ExecutionResult.Throw) {
                Try.Catch destination = ((ExecutionResult.Throw<State>) res).getDestination();
                State exception = ((ExecutionResult.Throw<State>) res).getException();
                if (exception != null && destination.getExceptionType() != null) {
                    exception = cast(exception, destination.getExceptionType().getType());
                    return new ExecutionResult.Throw<>(destination, exception);
                }
            }
            return res;
        });
        return results;
    }

    @Override
    protected State arrayLength(State array) {
        expect(array, State.Property.ARRAY_OR_NULL);
        return State.OfType.INT;
    }

    @Override
    protected State arrayLoad(State array, State index) {
        expect(array, State.Property.ARRAY_OR_NULL);
        expect(index, State.Property.INTEGRAL);
        if (array instanceof State.OfType) {
            TypeSet elementType = getComponentType((State.OfType) array);
            if (!elementType.isSingleType() && elementType.matches(Types::isReferenceType) == TriState.FALSE) {
                throw new VerifyException("Cannot deduce component type for " + array);
            }
            return new State.OfType(elementType);
        } else { // NULL
            ArrayLoadStore instruction = (ArrayLoadStore) context.getInstruction();
            switch (instruction.getElementType()) {
                case BOOLEAN:
                    return State.OfType.BOOLEAN;
                case BYTE:
                    return State.OfType.BYTE;
                case SHORT:
                    return State.OfType.SHORT;
                case CHAR:
                    return State.OfType.CHAR;
                case INT_FLOAT:
                    return State.OfType.INT;
                case WIDE:
                    return State.WIDE;
                case REFERENCE:
                    return State.UNKNOWN_TYPE;
                default:
                    throw new AssertionError();
            }
        }
    }

    @Override
    protected void arrayStore(State array, State index, State value) {
        expect(array, State.Property.ARRAY_OR_NULL);
        expect(index, State.Property.INTEGRAL);
        if (array instanceof State.OfType) {
            TypeSet elementType = getComponentType((State.OfType) array);
            if (hasProperty(new State.OfType(elementType), State.Property.INTEGRAL)) {
                expect(value, State.Property.INTEGRAL);
            } else {
                expectAssignableTo(value, elementType);
            }
        } else { // NULL
            ArrayLoadStore instruction = (ArrayLoadStore) context.getInstruction();
            switch (instruction.getElementType()) {
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case CHAR:
                case INT_FLOAT:
                    expect(value, State.Property.INTEGRAL);
                    break;
                case WIDE:
                    expect(value, State.Property.WIDE);
                    break;
                case REFERENCE:
                    expect(value, State.Property.REFERENCE_OR_NULL);
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    private TypeSet getComponentType(State.OfType arrayType) {
        TypeSet reduced = arrayType.getTypes().mapNullable(t -> Types.isArrayType(t) ?
                Types.getComponentType(t) :
                null);
        if (reduced == null) {
            throw new DexVerifyException("Input variable for array load/store is not an array: " + arrayType);
        }
        return reduced;
    }

    @Override
    protected State binaryOperation(BinaryOperation.Type type, State lhs, State rhs) {
        switch (type) {
            case COMPARE_FLOAT_BIAS_L:
            case COMPARE_FLOAT_BIAS_G:
                expectAssignableTo(lhs, Type.FLOAT_TYPE);
                expectAssignableTo(rhs, Type.FLOAT_TYPE);
                return State.OfType.INT;
            case COMPARE_DOUBLE_BIAS_L:
            case COMPARE_DOUBLE_BIAS_G:
                expectAssignableTo(lhs, Type.DOUBLE_TYPE);
                expectAssignableTo(rhs, Type.DOUBLE_TYPE);
                return State.OfType.INT;
            case COMPARE_LONG:
                expectAssignableTo(lhs, Type.LONG_TYPE);
                expectAssignableTo(rhs, Type.LONG_TYPE);
                return State.OfType.INT;
            case ADD_INT:
            case SUB_INT:
            case MUL_INT:
            case DIV_INT:
            case REM_INT:
            case SHL_INT:
            case SHR_INT:
            case USHR_INT:
                expect(lhs, State.Property.INTEGRAL);
                expect(rhs, State.Property.INTEGRAL);
                return State.OfType.INT;
            case AND_INT:
            case OR_INT:
            case XOR_INT:
                expect(lhs, State.Property.INTEGRAL);
                expect(rhs, State.Property.INTEGRAL);

                return StateMerger.mergeWithOp(null, lhs, rhs, type);
            case ADD_LONG:
            case SUB_LONG:
            case MUL_LONG:
            case DIV_LONG:
            case REM_LONG:
            case AND_LONG:
            case OR_LONG:
            case XOR_LONG:
                expectAssignableTo(lhs, Type.LONG_TYPE);
                expectAssignableTo(rhs, Type.LONG_TYPE);
                return State.OfType.LONG;
            case SHL_LONG:
            case SHR_LONG:
            case USHR_LONG:
                expectAssignableTo(lhs, Type.LONG_TYPE);
                expectAssignableTo(rhs, Type.INT_TYPE);
                return State.OfType.LONG;
            case ADD_FLOAT:
            case SUB_FLOAT:
            case MUL_FLOAT:
            case DIV_FLOAT:
            case REM_FLOAT:
                expectAssignableTo(lhs, Type.FLOAT_TYPE);
                expectAssignableTo(rhs, Type.FLOAT_TYPE);
                return State.OfType.FLOAT;
            case ADD_DOUBLE:
            case SUB_DOUBLE:
            case MUL_DOUBLE:
            case DIV_DOUBLE:
            case REM_DOUBLE:
                expectAssignableTo(lhs, Type.DOUBLE_TYPE);
                expectAssignableTo(rhs, Type.DOUBLE_TYPE);
                return State.OfType.DOUBLE;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected State literalBinaryOperation(LiteralBinaryOperation.Type type, State lhs, short rhs) {
        // TODO: is this restricted to int only?
        expect(lhs, State.Property.INTEGRAL);
        switch (type) {
            case AND:
                return StateMerger.mergeWithOp(null, lhs, State.Narrow.forValue(rhs),
                                               BinaryOperation.Type.AND_INT);
            case OR:
                return StateMerger.mergeWithOp(null, lhs, State.Narrow.forValue(rhs),
                                               BinaryOperation.Type.OR_INT);
            case XOR:
                return StateMerger.mergeWithOp(null, lhs, State.Narrow.forValue(rhs),
                                               BinaryOperation.Type.XOR_INT);
            default:
                return State.OfType.INT;
        }
    }

    @Override
    protected TriState branch(Branch.Type type, State lhs, State rhs) {
        if (type != Branch.Type.EQUAL) {
            expect(lhs, State.Property.INTEGRAL);
            expect(rhs, State.Property.INTEGRAL);
        }
        return super.branch(type, lhs, rhs);
    }

    @Override
    protected TriState branchZero(Branch.Type type, State lhs) {
        switch (type) {
            case EQUAL:
                expect(lhs, State.Property.INTEGRAL_REFERENCE_OR_NULL);
                // i refuse to implement the instanceof narrowing optimization. just no.
                break;
            case LESS_THAN:
            case GREATER_THAN:
                expect(lhs, State.Property.INTEGRAL);
                break;
        }
        return super.branchZero(type, lhs);
    }

    @Override
    protected void checkCast(TypeMirror type, State value) {
        expect(value, State.Property.REFERENCE_OR_NULL);
        // narrowing is done above in execute
    }

    @Override
    protected State constant(int narrow) {
        return State.Narrow.forValue(narrow);
    }

    @Override
    protected State constant(long wide) {
        return State.WIDE;
    }

    @Override
    protected State constant(String string) {
        return new State.OfType(Type.getType(String.class));
    }

    @Override
    protected State constant(TypeMirror type) {
        return new State.OfType(Type.getType(Class.class));
    }

    @Override
    protected State constantNull() {
        return State.NULL;
    }

    @Override
    protected void fillArray(State array, PrimitiveIterable items) {
        expect(array, State.Property.REFERENCE_OR_NULL);
        if (array instanceof State.OfType) {
            if (items instanceof BooleanIterable) {
                expectAssignableTo(array, Type.getType(boolean[].class));
            } else if (items instanceof ByteIterable) {
                expectAssignableTo(array, Type.getType(byte[].class));
            } else if (items instanceof ShortIterable) {
                if (array.isAssignableTo(classpath, Type.getType(char[].class)) == TriState.FALSE) {
                    expectAssignableTo(array, Type.getType(short[].class));
                }
            } else if (items instanceof CharIterable) {
                if (array.isAssignableTo(classpath, Type.getType(short[].class)) == TriState.FALSE) {
                    expectAssignableTo(array, Type.getType(char[].class));
                }
            } else if (items instanceof IntIterable) {
                // sometimes we fill float[]s with int iterables because the two are indistinguishable at disassembly
                if (array.isAssignableTo(classpath, Type.getType(float[].class)) == TriState.FALSE) {
                    expectAssignableTo(array, Type.getType(int[].class));
                }
            } else if (items instanceof FloatIterable) {
                expectAssignableTo(array, Type.getType(float[].class));
            } else if (items instanceof LongIterable) {
                // sometimes we fill double[]s with long iterables because the two are indistinguishable at disassembly
                if (array.isAssignableTo(classpath, Type.getType(double[].class)) == TriState.FALSE) {
                    expectAssignableTo(array, Type.getType(long[].class));
                }
            } else if (items instanceof DoubleIterable) {
                expectAssignableTo(array, Type.getType(double[].class));
            } else {
                throw new AssertionError();
            }
        }
    }

    @Override
    protected State instanceOf(TypeMirror type, State value) {
        expect(value, State.Property.REFERENCE_OR_NULL);
        return State.OfType.BOOLEAN;
    }

    private void checkParameters(Invoke.Type type, MethodMirror method, List<State> parameters, boolean ignoreFirst) {
        List<Type> argumentTypes = Instructions.getInvokeParameterTypes(method, type);
        assert argumentTypes.size() == parameters.size() :
                type + " " + method + "  " + argumentTypes + " " + parameters;
        for (int i = 0; i < argumentTypes.size(); i++) {
            if (ignoreFirst && i == 0) { continue; }
            expectAssignableTo(parameters.get(i), argumentTypes.get(i));
        }
    }

    @Override
    protected State invoke(Invoke.Type type, MethodMirror method, List<State> parameters) {
        checkParameters(type, method, parameters, false);
        if (type == Invoke.Type.NEW_INSTANCE) {
            return new State.OfType(method.getDeclaringType().getType());
        } else {
            if (method.getType().getReturnType().equals(Type.VOID_TYPE)) {
                throw new DexVerifyException("Attempted to get return value of VOID method: " + context);
            }
            return new State.OfType(method.getType().getReturnType());
        }
    }

    @Override
    protected void invokeVoid(Invoke.Type type, MethodMirror method, List<State> parameters) {
        boolean ignoreFirst = type == Invoke.Type.SPECIAL && method.isConstructor();
        checkParameters(type, method, parameters, ignoreFirst);
    }

    @Override
    protected State instanceLoad(State instance, FieldMirror field) {
        expectAssignableTo(instance, field.getDeclaringType(), true);
        return new State.OfType(field.getType().getType());
    }

    @Override
    protected State staticLoad(FieldMirror field) {
        return new State.OfType(field.getType().getType());
    }

    @Override
    protected void instanceStore(State instance, FieldMirror field, State value) {
        expectAssignableTo(instance, field.getDeclaringType(), true);
        expectAssignableTo(value, field.getType().getType());
    }

    @Override
    protected void staticStore(FieldMirror field, State value) {
        expectAssignableTo(value, field.getType().getType());
    }

    @Override
    protected void monitor(Monitor.Type type, State monitor) {
        expect(monitor, State.Property.REFERENCE_OR_NULL);
    }

    @Override
    protected State newArray(ArrayTypeMirror type, State length) {
        expect(length, State.Property.INTEGRAL);
        return new State.OfType(type.getType());
    }

    @Override
    protected State newArray(ArrayTypeMirror type, List<State> items) {
        for (State item : items) {
            expectAssignableTo(item, type.getComponentType());
        }
        return new State.OfType(type.getType());
    }

    @Override
    protected IntSet switch_(State input, IntSet branches, int defaultMarker) {
        expect(input, State.Property.INTEGRAL);
        return super.switch_(input, branches, defaultMarker);
    }

    @Override
    protected InstructionThrow throw_(State exception) {
        expectAssignableTo(exception, Type.getType(Throwable.class));
        // todo: is the ART verifier smart enough to only enter matching catch clauses here?
        return super.throw_(exception);
    }

    @Override
    protected State unaryOperation(UnaryOperation.Type type, State operand) {
        switch (type) {
            case NEGATE_INT:
            case NOT_INT:
                // TODO: is this restricted to int only?
                expect(operand, State.Property.INTEGRAL);
                return State.OfType.INT;
            case NEGATE_LONG:
            case NOT_LONG:
                expectAssignableTo(operand, Type.LONG_TYPE);
                return State.OfType.LONG;
            case NEGATE_FLOAT:
                expectAssignableTo(operand, Type.FLOAT_TYPE);
                return State.OfType.FLOAT;
            case NEGATE_DOUBLE:
                expectAssignableTo(operand, Type.DOUBLE_TYPE);
                return State.OfType.DOUBLE;
            case INT_TO_LONG:
                // TODO: is this restricted to int only?
                expect(operand, State.Property.INTEGRAL);
                return State.OfType.LONG;
            case INT_TO_FLOAT:
                // TODO: is this restricted to int only?
                expect(operand, State.Property.INTEGRAL);
                return State.OfType.FLOAT;
            case INT_TO_DOUBLE:
                // TODO: is this restricted to int only?
                expect(operand, State.Property.INTEGRAL);
                return State.OfType.DOUBLE;
            case LONG_TO_INT:
                expectAssignableTo(operand, Type.LONG_TYPE);
                return State.OfType.INT;
            case LONG_TO_FLOAT:
                expectAssignableTo(operand, Type.LONG_TYPE);
                return State.OfType.FLOAT;
            case LONG_TO_DOUBLE:
                expectAssignableTo(operand, Type.LONG_TYPE);
                return State.OfType.DOUBLE;
            case FLOAT_TO_INT:
                expectAssignableTo(operand, Type.FLOAT_TYPE);
                return State.OfType.INT;
            case FLOAT_TO_LONG:
                expectAssignableTo(operand, Type.FLOAT_TYPE);
                return State.OfType.LONG;
            case FLOAT_TO_DOUBLE:
                expectAssignableTo(operand, Type.FLOAT_TYPE);
                return State.OfType.DOUBLE;
            case DOUBLE_TO_INT:
                expectAssignableTo(operand, Type.DOUBLE_TYPE);
                return State.OfType.INT;
            case DOUBLE_TO_LONG:
                expectAssignableTo(operand, Type.DOUBLE_TYPE);
                return State.OfType.LONG;
            case DOUBLE_TO_FLOAT:
                expectAssignableTo(operand, Type.DOUBLE_TYPE);
                return State.OfType.FLOAT;
            case INT_TO_BYTE:
                // TODO: is this restricted to int only?
                expect(operand, State.Property.INTEGRAL);
                return State.OfType.BYTE;
            case INT_TO_CHAR:
                // TODO: is this restricted to int only?
                expect(operand, State.Property.INTEGRAL);
                return State.OfType.CHAR;
            case INT_TO_SHORT:
                // TODO: is this restricted to int only?
                expect(operand, State.Property.INTEGRAL);
                return State.OfType.SHORT;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected void return_() {
        if (methodMirror.getReturnType() != null) {
            throw new DexVerifyException("Returned nothing from a method that is not a void");
        }
    }

    @Override
    protected void return_(State value) {
        if (methodMirror.getReturnType() == null) {
            throw new DexVerifyException("Returned " + value + " from void method");
        }
        expectAssignableTo(value, methodMirror.getReturnType());
    }
}
