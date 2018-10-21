package at.yawk.valda.ir;

import java.util.function.BooleanSupplier;

/**
 * @author yawkat
 */
public enum TriState {
    TRUE,
    MAYBE,
    FALSE;

    public static TriState valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    public TriState or(TriState other) {
        if (this == TRUE || other == TRUE) { return TRUE; }
        if (this == MAYBE || other == MAYBE) { return MAYBE; }
        return FALSE;
    }

    public TriState and(TriState other) {
        if (this == FALSE || other == FALSE) { return FALSE; }
        if (this == MAYBE || other == MAYBE) { return MAYBE; }
        return TRUE;
    }

    public boolean asBoolean(BooleanSupplier maybe) {
        if (this == MAYBE) {
            return maybe.getAsBoolean();
        } else {
            return this == TRUE;
        }
    }

    public boolean asBoolean() {
        return asBoolean(() -> {
            throw new IllegalStateException("MAYBE");
        });
    }
}
