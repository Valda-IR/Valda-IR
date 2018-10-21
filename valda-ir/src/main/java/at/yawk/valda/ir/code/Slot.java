package at.yawk.valda.ir.code;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author yawkat
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class Slot {
    @Getter private final Arity arity;
    @Getter private final String name;

    static <I extends Instruction> Slot single(
            String name,
            Function<I, LocalVariable> get,
            BiConsumer<I, LocalVariable> set
    ) {
        return optional(Arity.SINGLE, name, get, set);
    }

    static <I extends Instruction> Slot optional(
            String name,
            Function<I, LocalVariable> get,
            BiConsumer<I, LocalVariable> set
    ) {
        return optional(Arity.OPTIONAL, name, get, set);
    }

    @SuppressWarnings("unchecked")
    private static <I extends Instruction> Slot optional(
            Arity arity,
            String name,
            Function<I, LocalVariable> get,
            BiConsumer<I, LocalVariable> set
    ) {
        return new Slot(arity, name) {
            @Override
            public LocalVariable getVariable(Instruction instruction) {
                return get.apply((I) instruction);
            }

            @Override
            public void setVariable(Instruction instruction, LocalVariable variable) {
                set.accept((I) instruction, variable);
            }

            @Override
            public List<LocalVariable> getVariables(Instruction instruction) {
                LocalVariable v = getVariable(instruction);
                return v == null ? Collections.emptyList() : Collections.singletonList(v);
            }

            @Override
            public void setVariables(Instruction instruction, List<LocalVariable> variable) {
                if (arity == Arity.OPTIONAL && variable.isEmpty()) {
                    setVariable(instruction, null);
                } else {
                    setVariable(instruction, Iterables.getOnlyElement(variable));
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <I extends Instruction> Slot variadic(
            String name,
            Function<I, List<LocalVariable>> get,
            BiConsumer<I, List<LocalVariable>> set
    ) {
        return new Slot(Arity.VARIADIC, name) {
            @Override
            public List<LocalVariable> getVariables(Instruction instruction) {
                return get.apply((I) instruction);
            }

            @Override
            public void setVariables(Instruction instruction, List<LocalVariable> variable) {
                set.accept((I) instruction, variable);
            }
        };
    }

    public LocalVariable getVariable(Instruction instruction) {
        if (arity == Arity.VARIADIC) { throw new UnsupportedOperationException(); }
        return Iterables.getOnlyElement(getVariables(instruction));
    }

    public abstract List<LocalVariable> getVariables(Instruction instruction);

    public void setVariable(Instruction instruction, LocalVariable variable) {
        if (arity == Arity.VARIADIC) { throw new UnsupportedOperationException(); }
        setVariables(instruction, Collections.singletonList(variable));
    }

    public abstract void setVariables(Instruction instruction, List<LocalVariable> variables);

    public void setVariable(Instruction instruction, int index, LocalVariable variable) {
        List<LocalVariable> variables = new ArrayList<>(getVariables(instruction));
        variables.set(index, variable);
        setVariables(instruction, variables);
    }

    public enum Arity {
        OPTIONAL,
        SINGLE,
        VARIADIC
    }
}
