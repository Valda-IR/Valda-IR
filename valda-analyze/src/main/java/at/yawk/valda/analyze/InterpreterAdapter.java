package at.yawk.valda.analyze;

import at.yawk.valda.ir.ArrayTypeMirror;
import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeMirrors;
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
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.Instructions;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.Monitor;
import at.yawk.valda.ir.code.Move;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Return;
import at.yawk.valda.ir.code.Switch;
import at.yawk.valda.ir.code.TerminatingInstruction;
import at.yawk.valda.ir.code.Throw;
import at.yawk.valda.ir.code.Try;
import at.yawk.valda.ir.code.UnaryOperation;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.api.PrimitiveIterable;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@Slf4j
public abstract class InterpreterAdapter<V> implements Interpreter<V> {
    @Override
    public <K> StateCollector<K, V> createStateCollector() {
        return new FastUnorderedStateCollector<>((m1, m2) -> {
            Map<LocalVariable, V> merged = new HashMap<>(m1);
            mergeInto(merged, m2);
            return merged;
        });
    }

    private void mergeInto(Map<LocalVariable, V> merged, Map<LocalVariable, V> map) {
        for (Map.Entry<LocalVariable, V> entry : map.entrySet()) {
            V lhs = merged.get(entry.getKey());
            V rhs = entry.getValue();
            if (lhs == null || lhs.equals(rhs)) {
                merged.put(entry.getKey(), rhs);
            } else {
                V v = merge(entry.getKey(), lhs, rhs);
                log.trace("Merged {} u {} -> {}", lhs, rhs, v);
                merged.put(entry.getKey(), v);
            }
        }
    }

    @NonNull
    protected V merge(LocalVariable variable, @NonNull V left, @NonNull V right) {
        return defaultValue();
    }

    @Override
    public Map<LocalVariable, V> getParameterValues(MethodBody body) {
        return body.getParameters().stream().collect(Collectors.toMap(k -> k, this::getParameterValue));
    }

    protected V getParameterValue(@NonNull LocalVariable variable) {
        return defaultValue();
    }

    @NonNull
    @Override
    public Iterable<ExecutionResult<V>> execute(@NonNull ExecutionContext<V> context) {
        List<ExecutionResult<V>> results = new ArrayList<>();
        Instruction instruction = context.getInstruction();
        if (instruction instanceof TerminatingInstruction) {
            if (instruction instanceof GoTo) {
                goTo();
                results.add(new ExecutionResult.Branch<>(((GoTo) instruction).getTarget()));
            } else if (instruction instanceof Branch) {
                Branch.Type type = ((Branch) instruction).getType();
                V lhs = context.getInput(((Branch) instruction).getLhs());
                TriState res;
                if (((Branch) instruction).getRhs() != null) {
                    res = branch(type,
                                 lhs,
                                 context.getInput(((Branch) instruction).getRhs()));
                } else {
                    res = branchZero(type, lhs);
                }
                if (res != TriState.FALSE) {
                    results.add(new ExecutionResult.Branch<>(((Branch) instruction).getBranchTrue()));
                }
                if (res != TriState.TRUE) {
                    results.add(new ExecutionResult.Branch<>(((Branch) instruction).getBranchFalse()));
                }
            } else if (instruction instanceof Return) {
                LocalVariable returnValue = ((Return) instruction).getReturnValue();
                if (returnValue == null) {
                    return_();
                } else {
                    return_(context.getInput(returnValue));
                }
                results.add(new ExecutionResult.Return<>());
            } else if (instruction instanceof Switch) {
                IntSet keySet = ((Switch) instruction).getBranches().keySet();
                int defaultMarker;
                do {
                    defaultMarker = ThreadLocalRandom.current().nextInt();
                } while (keySet.contains(defaultMarker));
                IntSet res = switch_(
                        context.getInput(((Switch) instruction).getOperand()),
                        keySet,
                        defaultMarker
                );
                int finalDefaultMarker = defaultMarker;
                res.forEach(i -> {
                    if (i == finalDefaultMarker) {
                        results.add(new ExecutionResult.Branch<>(((Switch) instruction).getDefaultBranch()));
                    } else {
                        BasicBlock block = ((Switch) instruction).getBranches().get(i);
                        if (block == null) {
                            throw new AnalyzerException(
                                    "Interpreter returned switch branch " + i + " but was not on instruction!");
                        }
                        results.add(new ExecutionResult.Branch<>(block));
                    }
                });
            } else if (instruction instanceof Throw) {
                InstructionThrow instructionThrow = throw_(context.getInput(((Throw) instruction).getException()));
                handleAlwaysThrow(results, context.getBlock().getTry(), instructionThrow);
            }
        } else {
            boolean alwaysThrow = false;
            try {
                results.add(executeNonTerminal(context));
            } catch (InstructionThrow instructionThrow) {
                alwaysThrow = true;
                handleAlwaysThrow(results, context.getBlock().getTry(), instructionThrow);
            }
            if (!alwaysThrow) {
                InstructionThrow t = maybeThrow(instruction);
                if (t != null) {
                    handleAlwaysThrow(results, context.getBlock().getTry(), t);
                }
            }
        }
        return results;
    }

    /**
     * If {@link #executeNonTerminal(ExecutionContext)} did not throw an {@link InstructionThrow}, return {@code
     * true} if this instruction might still throw optionally.
     */
    @Nullable
    protected InstructionThrow maybeThrow(Instruction instruction) {
        // we don't use the defaultValue here immediately - that is done below in handleAlwaysThrow since we don't
        // need the defaultValue for a throw that has no enclosing catches anyway
        return Instructions.canThrow(instruction) ? new InstructionThrow() : null;
    }

    private void handleAlwaysThrow(
            List<ExecutionResult<V>> results,
            @Nullable Try try_,
            InstructionThrow instructionThrow
    ) {
        TypeMirror type = instructionThrow.type;
        boolean foundExact = false;
        if (try_ != null) {
            //noinspection unchecked
            V exc = (V) instructionThrow.exception;

            for (Try.Catch handler : try_.getHandlers()) {
                TypeMirror handlerType = handler.getExceptionType();

                TriState supertype;
                if (handlerType == null) {
                    supertype = TriState.TRUE;
                } else if (type != null) {
                    supertype = TypeMirrors.isSupertype(handlerType, type);
                } else {
                    supertype = TriState.MAYBE;
                }
                if (supertype != TriState.FALSE) {
                    // populated lazily since defaultValue may not be implemented
                    if (exc == null && handler.getHandler().getExceptionVariable() != null) {
                        exc = defaultValue();
                    }
                    results.add(new ExecutionResult.Throw<>(handler, exc));
                }
                if (supertype == TriState.TRUE) {
                    // definitely using this catch, so don't need to visit following handlers
                    foundExact = true;
                    break;
                }
            }
        }
        if (!foundExact) {
            // might return from method
            results.add(new ExecutionResult.ThrowMethod<>());
        }
    }

    protected ExecutionResult.Continue<V> executeNonTerminal(ExecutionContext<V> context) throws InstructionThrow {
        Instruction instruction = context.getInstruction();
        if (instruction instanceof ArrayLength) {
            return ExecutionResult.Continue.<V>builder().outputVariable(
                    ((ArrayLength) instruction).getTarget(),
                    arrayLength(context.getInput(((ArrayLength) instruction).getOperand()))
            ).build();
        } else if (instruction instanceof ArrayLoadStore) {
            if (((ArrayLoadStore) instruction).getType() == LoadStore.Type.LOAD) {
                return ExecutionResult.Continue.<V>builder().outputVariable(
                        ((ArrayLoadStore) instruction).getValue(),
                        arrayLoad(context.getInput(((ArrayLoadStore) instruction).getArray()),
                                  context.getInput(((ArrayLoadStore) instruction).getIndex()))
                ).build();
            } else {
                arrayStore(
                        context.getInput(((ArrayLoadStore) instruction).getArray()),
                        context.getInput(((ArrayLoadStore) instruction).getIndex()),
                        context.getInput(((ArrayLoadStore) instruction).getValue()));
                return ExecutionResult.Continue.noChange();
            }
        } else if (instruction instanceof BinaryOperation) {
            return ExecutionResult.Continue.<V>builder().outputVariable(
                    ((BinaryOperation) instruction).getDestination(),
                    binaryOperation(
                            ((BinaryOperation) instruction).getType(),
                            context.getInput(((BinaryOperation) instruction).getLhs()),
                            context.getInput(((BinaryOperation) instruction).getRhs())
                    )
            ).build();
        } else if (instruction instanceof CheckCast) {
            checkCast(((CheckCast) instruction).getType(), context.getInput(((CheckCast) instruction).getVariable()));
            return ExecutionResult.Continue.noChange();
        } else if (instruction instanceof Const) {
            Const.Value value = ((Const) instruction).getValue();
            LocalVariable target = ((Const) instruction).getTarget();
            if (value instanceof Const.Narrow) {
                return ExecutionResult.Continue.<V>builder().outputVariable(target,
                                                                            constant(((Const.Narrow) value).getValue()))
                        .build();
            } else if (value instanceof Const.Wide) {
                return ExecutionResult.Continue.<V>builder().outputVariable(target,
                                                                            constant(((Const.Wide) value).getValue()))
                        .build();
            } else if (value instanceof Const.String) {
                return ExecutionResult.Continue.<V>builder().outputVariable(target,
                                                                            constant(((Const.String) value).getValue()))
                        .build();
            } else if (value instanceof Const.Class) {
                return ExecutionResult.Continue.<V>builder().outputVariable(target,
                                                                            constant(((Const.Class) value).getValue()))
                        .build();
            } else if (value instanceof Const.Null) {
                return ExecutionResult.Continue.<V>builder().outputVariable(target, constantNull()).build();
            } else {
                throw new AssertionError();
            }
        } else if (instruction instanceof FillArray) {
            fillArray(context.getInput(((FillArray) instruction).getArray()), ((FillArray) instruction).getContents());
            return ExecutionResult.Continue.noChange();
        } else if (instruction instanceof InstanceOf) {
            return ExecutionResult.Continue.<V>builder().outputVariable(
                    ((InstanceOf) instruction).getTarget(),
                    instanceOf(
                            ((InstanceOf) instruction).getType(),
                            context.getInput(((InstanceOf) instruction).getOperand())
                    )
            ).build();
        } else if (instruction instanceof Invoke) {
            List<V> parameters = Lists.transform(((Invoke) instruction).getParameters(), context::getInput);
            if (((Invoke) instruction).getMethod().getType().getReturnType().equals(Type.VOID_TYPE) &&
                ((Invoke) instruction).getType() != Invoke.Type.NEW_INSTANCE) {
                invokeVoid(((Invoke) instruction).getType(),
                           ((Invoke) instruction).getMethod(),
                           parameters);
                return ExecutionResult.Continue.noChange();
            } else {
                V ret = invoke(((Invoke) instruction).getType(),
                               ((Invoke) instruction).getMethod(),
                               parameters);
                if (((Invoke) instruction).getReturnValue() != null) {
                    return ExecutionResult.Continue.<V>builder().outputVariable(((Invoke) instruction).getReturnValue(),
                                                                                ret).build();
                } else {
                    return ExecutionResult.Continue.noChange();
                }
            }
        } else if (instruction instanceof LiteralBinaryOperation) {
            return ExecutionResult.Continue.<V>builder().outputVariable(
                    ((LiteralBinaryOperation) instruction).getDestination(),
                    literalBinaryOperation(((LiteralBinaryOperation) instruction).getType(),
                                           context.getInput(((LiteralBinaryOperation) instruction).getLhs()),
                                           ((LiteralBinaryOperation) instruction).getRhs())
            ).build();
        } else if (instruction instanceof LoadStore) {
            LocalVariable instance = ((LoadStore) instruction).getInstance();
            FieldMirror field = ((LoadStore) instruction).getField();
            if (((LoadStore) instruction).getType() == LoadStore.Type.LOAD) {
                return ExecutionResult.Continue.<V>builder().outputVariable(
                        ((LoadStore) instruction).getValue(),
                        instance == null ?
                                staticLoad(field) :
                                instanceLoad(context.getInput(instance), field)
                ).build();
            } else {
                if (instance == null) {
                    staticStore(field,
                                context.getInput(((LoadStore) instruction).getValue()));
                } else {
                    instanceStore(context.getInput(instance),
                                  field,
                                  context.getInput(((LoadStore) instruction).getValue()));
                }
                return ExecutionResult.Continue.noChange();
            }
        } else if (instruction instanceof Monitor) {
            monitor(((Monitor) instruction).getType(), context.getInput(((Monitor) instruction).getMonitor()));
            return ExecutionResult.Continue.noChange();
        } else if (instruction instanceof Move) {
            return ExecutionResult.Continue.<V>builder().outputVariable(
                    ((Move) instruction).getTo(),
                    context.getInput(((Move) instruction).getFrom())
            ).build();
        } else if (instruction instanceof NewArray) {
            return ExecutionResult.Continue.<V>builder().outputVariable(
                    ((NewArray) instruction).getTarget(),
                    ((NewArray) instruction).hasVariables() ?
                            newArray(((NewArray) instruction).getType(),
                                     Lists.transform(((NewArray) instruction).getVariables(), context::getInput)) :
                            newArray(((NewArray) instruction).getType(),
                                     context.getInput(((NewArray) instruction).getLength()))
            ).build();
        } else if (instruction instanceof UnaryOperation) {
            return ExecutionResult.Continue.<V>builder().outputVariable(
                    ((UnaryOperation) instruction).getDestination(),
                    unaryOperation(((UnaryOperation) instruction).getType(),
                                   context.getInput(((UnaryOperation) instruction).getSource()))
            ).build();
        } else {
            throw new AssertionError();
        }
    }

    protected V arrayLength(V array) throws InstructionThrow {
        return defaultValue();
    }

    protected V arrayLoad(V array, V index) throws InstructionThrow {
        return defaultValue();
    }

    protected void arrayStore(V array, V index, V value) throws InstructionThrow {
    }

    protected V binaryOperation(BinaryOperation.Type type, V lhs, V rhs) {
        return defaultValue();
    }

    protected V literalBinaryOperation(LiteralBinaryOperation.Type type, V lhs, short rhs) {
        return defaultValue();
    }

    protected TriState branch(Branch.Type type, V lhs, V rhs) {
        return TriState.MAYBE;
    }

    protected TriState branchZero(Branch.Type type, V lhs) {
        return branch(type, lhs, constant(0));
    }

    protected void checkCast(TypeMirror type, V value) throws InstructionThrow {
    }

    protected V constant(int narrow) {
        return defaultValue();
    }

    protected V constant(long wide) {
        return defaultValue();
    }

    protected V constant(String string) {
        return defaultValue();
    }

    protected V constant(TypeMirror type) {
        return defaultValue();
    }

    protected V constantNull() {
        return defaultValue();
    }

    protected void fillArray(V array, PrimitiveIterable items) throws InstructionThrow {
    }

    protected void goTo() {
    }

    protected V instanceOf(TypeMirror type, V value) {
        return defaultValue();
    }

    protected V invoke(Invoke.Type type, MethodMirror method, List<V> parameters) throws InstructionThrow {
        return defaultValue();
    }

    protected void invokeVoid(Invoke.Type type, MethodMirror method, List<V> parameters) throws InstructionThrow {
        invoke(type, method, parameters);
    }

    protected V instanceLoad(V instance, FieldMirror field) throws InstructionThrow {
        return defaultValue();
    }

    protected V staticLoad(FieldMirror field) throws InstructionThrow {
        return defaultValue();
    }

    protected void instanceStore(V instance, FieldMirror field, V value) throws InstructionThrow {
    }

    protected void staticStore(FieldMirror field, V value) throws InstructionThrow {
    }

    protected void monitor(Monitor.Type type, V monitor) throws InstructionThrow {
    }

    protected V newArray(ArrayTypeMirror type, V length) throws InstructionThrow {
        return defaultValue();
    }

    protected V newArray(ArrayTypeMirror type, List<V> items) throws InstructionThrow {
        return defaultValue();
    }

    protected void return_(V value) {
    }

    protected void return_() {
    }

    /**
     * @param defaultMarker A branch value that is not in {@code branches}, returning which indicates the default
     *                      branch should be visited as well
     * @return The reachable branches, including {@code defaultMarker} if the default branch should be visited
     */
    protected IntSet switch_(V input, IntSet branches, int defaultMarker) {
        return branches.toImmutable().newWith(defaultMarker);
    }

    protected InstructionThrow throw_(V exception) {
        return new InstructionThrow(null, exception);
    }

    protected V unaryOperation(UnaryOperation.Type type, V operand) throws InstructionThrow {
        return defaultValue();
    }

    protected V defaultValue() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("ExceptionClassNameDoesntEndWithException")
    @RequiredArgsConstructor
    protected static class InstructionThrow extends Exception {
        /**
         * The <i>exact</i> type of this exception. This decides which handlers are entered!
         *
         * {@code null} signifies an unknown exception type.
         */
        @Nullable private final TypeMirror type;
        /**
         * Actually of type {@code V}.
         */
        private final Object exception;

        public InstructionThrow() {
            this(null, null);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
