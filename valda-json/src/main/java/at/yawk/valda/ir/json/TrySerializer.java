package at.yawk.valda.ir.json;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Try;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author yawkat
 */
public final class TrySerializer extends JsonSerializer<Try> {
    @Override
    public void serialize(Try value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        JsonSerializer<? super BasicBlock> blockSerializer = serializers.findValueSerializer(BasicBlock.class);
        TryHolder tryHolder = (TryHolder) serializers.getAttribute(TryHolder.KEY);
        if (tryHolder == null) {
            serializers.setAttribute(TryHolder.KEY, tryHolder = new TryHolder());
        }

        gen.writeStartObject(value);
        gen.writeFieldName("-id");
        gen.writeObject(tryHolder.uuids.computeIfAbsent(value, t -> UUID.randomUUID()));
        for (Try.Catch handler : value.getHandlers()) {
            gen.writeFieldName(
                    handler.getExceptionType() == null ? "" : handler.getExceptionType().getType().getClassName());
            blockSerializer.serialize(handler.getHandler(), gen, serializers);
        }
        gen.writeEndObject();
    }

    static class TryHolder {
        static final Object KEY = new Object();

        final Map<Try, UUID> uuids = new IdentityHashMap<>();
    }
}
