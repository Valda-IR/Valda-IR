package at.yawk.valda.ir.code;

import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.MethodReference;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Singular;
import lombok.ToString;

/**
 * @author yawkat
 */
@EqualsAndHashCode(callSuper = false)
@ToString
public final class Invoke extends Instruction {
    public static final Slot PARAMETERS = Slot.variadic("parameters", Invoke::getParameters, Invoke::setParameters);
    public static final Slot RETURN_VALUE = Slot.optional(
            "returnValue", Invoke::getReturnValue, Invoke::setReturnValue);

    @Getter @Setter @NonNull private Type type;
    @NonNull private final MethodReference.Invoke method;
    @NonNull @Getter @Setter private List<LocalVariable> parameters;
    @Nullable @Getter @Setter private LocalVariable returnValue;

    @Builder
    private Invoke(
            @NonNull Type type,
            MethodMirror method,
            @NonNull @Singular List<LocalVariable> parameters,
            @Nullable LocalVariable returnValue
    ) {
        this.type = type;
        this.method = SecretsHolder.secrets.newInvoke(method, this);
        this.parameters = parameters;
        this.returnValue = returnValue;
    }

    public MethodMirror getMethod() {
        return method.getReferencedMethod();
    }

    @Override
    void linkClasspath() {
        method.getReferencedMethod().getReferences().add(method);
    }

    @Override
    void unlinkClasspath() {
        method.getReferencedMethod().getReferences().remove(method);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return Collections.singletonList(PARAMETERS);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(RETURN_VALUE);
    }

    public enum Type {
        /**
         * Normal invocation. For non-static methods this is {@code invoke-virtual}, for static methods it is
         * equivalent to {@link #SPECIAL}. For interfaces this is invoke-interface instead of invoke-virtual.
         */
        NORMAL,
        /**
         * Non-virtual invocation (java {@code invokespecial}) of a method. For static methods, this translates to
         * {@code invoke-static}. For private methods, this translates to {@code invoke-direct}. For other methods
         * (super calls) this translates to {@code invoke-super}.
         */
        SPECIAL,
        /**
         * New instance allocation together with constructor invocation. This is a pseudo-instruction and maps to a
         * {@code new-instance} and constructor call.
         */
        NEW_INSTANCE,
    }

    public static class InvokeBuilder {
        {
            type = Type.NORMAL;
        }

        public InvokeBuilder newInstance() {
            return type(Type.NEW_INSTANCE);
        }

        public InvokeBuilder special() {
            return type(Type.SPECIAL);
        }
    }
}
