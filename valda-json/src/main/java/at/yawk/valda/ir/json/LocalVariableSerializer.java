package at.yawk.valda.ir.json;

import at.yawk.valda.ir.code.LocalVariable;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * @author yawkat
 */
public final class LocalVariableSerializer extends JsonSerializer<LocalVariable> {
    @Override
    public void serialize(LocalVariable value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        String prefix;
        switch (value.getType()) {
            case NARROW:
                prefix = "n:";
                break;
            case WIDE:
                prefix = "w:";
                break;
            case REFERENCE:
                prefix = "r:";
                break;
            default:
                throw new AssertionError(value);
        }
        gen.writeString(prefix + value.getName());
    }
}
