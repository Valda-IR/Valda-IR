package at.yawk.valda.ir.code;

import at.yawk.valda.ir.ArrayTypeMirror;
import at.yawk.valda.ir.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Singular;

/**
 * @author yawkat
 */
public final class NewArray extends Instruction {
    public static final Slot TARGET = Slot.single("target", NewArray::getTarget, NewArray::setTarget);
    public static final Slot VARIABLES = Slot.variadic("variables", NewArray::getVariables, NewArray::setVariables);
    public static final Slot LENGTH = Slot.single("length", NewArray::getLength, NewArray::setLength);

    @NonNull @Getter @Setter private LocalVariable target;
    @Nullable private List<LocalVariable> variables;
    @Nullable private LocalVariable length;
    @SuppressWarnings("NullableProblems")
    @NonNull private TypeReference.NewArrayType type;

    @SuppressWarnings("NullableProblems")
    @Builder(builderClassName = "NewArrayVariableBuilder", builderMethodName = "variableBuilder")
    NewArray(
            @NonNull LocalVariable target,
            @NonNull ArrayTypeMirror type,
            @Singular @NonNull List<LocalVariable> variables
    ) {
        this.target = target;
        this.variables = variables;
        setType(type);
    }

    @SuppressWarnings("NullableProblems")
    @Builder(builderClassName = "NewArrayLengthBuilder", builderMethodName = "lengthBuilder")
    NewArray(@NonNull LocalVariable target, @NonNull ArrayTypeMirror type, @NonNull LocalVariable length) {
        this.target = target;
        this.length = length;
        setType(type);
    }

    public void setType(ArrayTypeMirror type) {
        if (isClasspathLinked()) {
            unlinkClasspath();
        }
        this.type = SecretsHolder.secrets.newArrayTypeReference(type, this);
        if (isClasspathLinked()) {
            linkClasspath();
        }
    }

    public ArrayTypeMirror getType() {
        return (ArrayTypeMirror) type.getReferencedType();
    }

    public boolean hasVariables() {
        return variables != null;
    }

    @NonNull
    public List<LocalVariable> getVariables() {
        if (!hasVariables()) { throw new IllegalStateException(); }
        return variables;
    }

    private void setVariables(@NonNull List<LocalVariable> variables) {
        if (!hasVariables()) { throw new IllegalStateException(); }
        this.variables = variables;
    }

    @NonNull
    public LocalVariable getLength() {
        if (hasVariables()) { throw new IllegalStateException(); }
        return length;
    }

    private void setLength(@NonNull LocalVariable length) {
        if (hasVariables()) { throw new IllegalStateException(); }
        this.length = length;
    }

    @Override
    public String toString() {
        return "NewArray{" +
               "target=" + getTarget() +
               (hasVariables() ? ", variables=" + variables : "") +
               (!hasVariables() ? ", length=" + length : "") +
               ", type=" + getType() +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof NewArray)) { return false; }
        NewArray newArray = (NewArray) o;
        return Objects.equals(getTarget(), newArray.getTarget()) &&
               Objects.equals(variables, newArray.variables) &&
               Objects.equals(length, newArray.length) &&
               Objects.equals(getType(), newArray.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, variables, length, type);
    }

    @Override
    void linkClasspath() {
        type.getReferencedType().getReferences().add(type);
    }

    @Override
    void unlinkClasspath() {
        type.getReferencedType().getReferences().remove(type);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        if (hasVariables()) {
            return ImmutableList.of(VARIABLES);
        } else {
            assert length != null;
            return ImmutableList.of(LENGTH);
        }
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of(TARGET);
    }
}
