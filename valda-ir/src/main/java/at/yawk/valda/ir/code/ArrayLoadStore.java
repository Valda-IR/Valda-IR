package at.yawk.valda.ir.code;

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
@Builder
@EqualsAndHashCode(callSuper = false)
@ToString
@Getter
@Setter
public final class ArrayLoadStore extends Instruction {
    public static final Slot ARRAY = Slot.single("array", ArrayLoadStore::getArray, ArrayLoadStore::setArray);
    public static final Slot INDEX = Slot.single("index", ArrayLoadStore::getIndex, ArrayLoadStore::setIndex);
    public static final Slot VALUE = Slot.single("value", ArrayLoadStore::getValue, ArrayLoadStore::setValue);

    @NonNull private final LoadStore.Type type;
    @NonNull private ElementType elementType;
    @NonNull private LocalVariable array;
    @NonNull private LocalVariable index;
    @NonNull private LocalVariable value;

    public static ArrayLoadStoreBuilder loadBoolean() {
        return builder().type(LoadStore.Type.LOAD).elementType(ElementType.BOOLEAN);
    }

    public static ArrayLoadStoreBuilder loadByte() {
        return builder().type(LoadStore.Type.LOAD).elementType(ElementType.BYTE);
    }

    public static ArrayLoadStoreBuilder loadShort() {
        return builder().type(LoadStore.Type.LOAD).elementType(ElementType.SHORT);
    }

    public static ArrayLoadStoreBuilder loadChar() {
        return builder().type(LoadStore.Type.LOAD).elementType(ElementType.CHAR);
    }

    public static ArrayLoadStoreBuilder loadIntFloat() {
        return builder().type(LoadStore.Type.LOAD).elementType(ElementType.INT_FLOAT);
    }

    public static ArrayLoadStoreBuilder loadWide() {
        return builder().type(LoadStore.Type.LOAD).elementType(ElementType.WIDE);
    }

    public static ArrayLoadStoreBuilder loadReference() {
        return builder().type(LoadStore.Type.LOAD).elementType(ElementType.REFERENCE);
    }

    public static ArrayLoadStoreBuilder storeBoolean() {
        return builder().type(LoadStore.Type.STORE).elementType(ElementType.BOOLEAN);
    }

    public static ArrayLoadStoreBuilder storeByte() {
        return builder().type(LoadStore.Type.STORE).elementType(ElementType.BYTE);
    }

    public static ArrayLoadStoreBuilder storeShort() {
        return builder().type(LoadStore.Type.STORE).elementType(ElementType.SHORT);
    }

    public static ArrayLoadStoreBuilder storeChar() {
        return builder().type(LoadStore.Type.STORE).elementType(ElementType.CHAR);
    }

    public static ArrayLoadStoreBuilder storeIntFloat() {
        return builder().type(LoadStore.Type.STORE).elementType(ElementType.INT_FLOAT);
    }

    public static ArrayLoadStoreBuilder storeWide() {
        return builder().type(LoadStore.Type.STORE).elementType(ElementType.WIDE);
    }

    public static ArrayLoadStoreBuilder storeReference() {
        return builder().type(LoadStore.Type.STORE).elementType(ElementType.REFERENCE);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return type == LoadStore.Type.LOAD ? ImmutableList.of(ARRAY, INDEX) : ImmutableList.of(ARRAY, INDEX, VALUE);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return type == LoadStore.Type.LOAD ? ImmutableList.of(VALUE) : ImmutableList.of();
    }

    public enum ElementType {
        BOOLEAN,
        BYTE,
        SHORT,
        CHAR,
        INT_FLOAT,
        WIDE,
        REFERENCE,
    }
}
