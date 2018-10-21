package at.yawk.valda.ir.code;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author yawkat
 */
@AllArgsConstructor(staticName = "create")
@EqualsAndHashCode(callSuper = false)
@ToString
@Getter
@Setter
public final class Return extends TerminatingInstruction {
    public static final Slot RETURN_VALUE = Slot.optional("length", Return::getReturnValue, Return::setReturnValue);

    @Nullable LocalVariable returnValue;

    public static Return createVoid() {
        return create(null);
    }

    @Override
    public List<BasicBlock> getSuccessors() {
        return Collections.emptyList();
    }

    @Override
    public void updateSuccessors(Function<BasicBlock, BasicBlock> updateFunction) {
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(RETURN_VALUE);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }
}
