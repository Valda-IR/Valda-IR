package at.yawk.valda.ir.code;

import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * @author yawkat
 */
@EqualsAndHashCode(callSuper = false)
@ToString
public final class CheckCast extends Instruction {
    public static final Slot VARIABLE = Slot.single("variable", CheckCast::getVariable, CheckCast::setVariable);

    @NonNull @Getter @Setter private LocalVariable variable;
    @NonNull private TypeReference.Cast type;

    private CheckCast(@NonNull LocalVariable variable, @NonNull TypeMirror type) {
        this.variable = variable;
        this.type = SecretsHolder.secrets.newCast(type, this);
    }

    @Builder
    public static CheckCast create(@NonNull LocalVariable variable, @NonNull TypeMirror type) {
        return new CheckCast(variable, type);
    }

    public TypeMirror getType() {
        return type.getReferencedType();
    }

    public void setType(TypeMirror type) {
        boolean linked = isClasspathLinked();
        if (linked) { unlinkClasspath(); }
        this.type = SecretsHolder.secrets.newCast(type, this);
        if (linked) { linkClasspath(); }
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
        return ImmutableList.of(VARIABLE);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }
}
