package at.yawk.valda.ir.code;

import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.FieldReference;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import javax.annotation.Nullable;
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
public final class LoadStore extends Instruction {
    public static final Slot INSTANCE = Slot.optional("instance", LoadStore::getInstance, LoadStore::setInstance);
    public static final Slot VALUE = Slot.single("value", LoadStore::getValue, LoadStore::setValue);

    @Getter private final Type type;
    @Getter @Setter @Nullable private LocalVariable instance;
    @SuppressWarnings("NullableProblems")
    @NonNull private FieldReference.LoadStore field;
    @Getter @Setter @NonNull private LocalVariable value;

    @Builder
    private LoadStore(
            @NonNull Type type,
            @Nullable LocalVariable instance,
            @NonNull FieldMirror field,
            @NonNull LocalVariable value
    ) {
        this.type = type;
        this.instance = instance;
        this.value = value;

        setField(field);
    }

    public static LoadStoreBuilder load() {
        return builder().type(Type.LOAD);
    }

    public static LoadStoreBuilder store() {
        return builder().type(Type.STORE);
    }

    public void setField(FieldMirror field) {
        if (isClasspathLinked()) {
            unlinkClasspath();
        }
        this.field = SecretsHolder.secrets.newLoadStoreReference(field, this);
        if (isClasspathLinked()) {
            linkClasspath();
        }
    }

    public FieldMirror getField() {
        return field.getReferencedField();
    }

    @Override
    void linkClasspath() {
        field.getReferencedField().getReferences().add(field);
    }

    @Override
    void unlinkClasspath() {
        field.getReferencedField().getReferences().remove(field);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return type == Type.STORE ? ImmutableList.of(INSTANCE, VALUE) : ImmutableList.of(INSTANCE);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return type == Type.LOAD ? ImmutableList.of(VALUE) : ImmutableList.of();
    }

    public enum Type {
        LOAD,
        STORE,
    }
}
