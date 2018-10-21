package at.yawk.valda.ir.json;

import at.yawk.valda.ir.code.LocalVariable;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * @author yawkat
 */
public final class LocalVariableDeserializer extends JsonDeserializer<LocalVariable> {
    @Override
    public LocalVariable deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        String name = p.getValueAsString();
        LocalVariable.Type type;
        if (name.startsWith("n:")) {
            type = LocalVariable.Type.NARROW;
            name = name.substring(2);
        } else if (name.startsWith("w:")) {
            type = LocalVariable.Type.WIDE;
            name = name.substring(2);
        } else if (name.startsWith("r:")) {
            type = LocalVariable.Type.REFERENCE;
            name = name.substring(2);
        } else {
            throw ctxt.weirdStringException(
                    name, LocalVariable.class, "Cannot deduce variable type: should start with n:, w: or r:");
        }

        return LocalVariable.create(type, name);
    }
}
