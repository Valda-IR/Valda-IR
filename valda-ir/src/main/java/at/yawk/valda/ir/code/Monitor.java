package at.yawk.valda.ir.code;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
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
public final class Monitor extends Instruction {
    public static final Slot MONITOR = Slot.single("monitor", Monitor::getMonitor, Monitor::setMonitor);

    @NonNull private final Type type;
    @NonNull private LocalVariable monitor;

    public static Monitor createEnter(LocalVariable monitor) {
        return create(Type.ENTER, monitor);
    }

    public static Monitor createExit(LocalVariable monitor) {
        return create(Type.EXIT, monitor);
    }

    @Override
    public Collection<Slot> getInputSlots() {
        return ImmutableList.of(MONITOR);
    }

    @Override
    public Collection<Slot> getOutputSlots() {
        return ImmutableList.of();
    }

    public enum Type {
        ENTER,
        EXIT,
    }
}
