package at.yawk.valda.ir.json;

import at.yawk.valda.ir.Classpath;
import com.fasterxml.jackson.databind.DatabindContext;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * @author yawkat
 */
@UtilityClass
public final class DexJson {
    public static final Object CLASSPATH_ATTRIBUTE_KEY = new Object();

    @NonNull
    public static Classpath getClasspath(DatabindContext ctx) {
        Object cp = ctx.getAttribute(CLASSPATH_ATTRIBUTE_KEY);
        if (cp == null) { throw new IllegalStateException("CLASSPATH_ATTRIBUTE_KEY not set"); }
        return (Classpath) cp;
    }

    public static void popMethodState(DatabindContext ctx) {
        ctx.setAttribute(TrySerializer.TryHolder.KEY, null);
    }
}
