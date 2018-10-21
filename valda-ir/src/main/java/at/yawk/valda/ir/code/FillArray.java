package at.yawk.valda.ir.code;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.collections.api.PrimitiveIterable;

/**
 * @author yawkat
 */
@Getter
@Setter
@AllArgsConstructor(staticName = "create")
@EqualsAndHashCode(callSuper = false)
@ToString
public final class FillArray extends Instruction {
    public static final Slot ARRAY = Slot.single("array", FillArray::getArray, FillArray::setArray);

    @NonNull private LocalVariable array;
    @NonNull private PrimitiveIterable contents;

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(ARRAY);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }
}
