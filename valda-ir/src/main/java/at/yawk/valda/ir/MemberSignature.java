package at.yawk.valda.ir;

import lombok.Value;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@Value
public final class MemberSignature {
    private final String name;
    private final Type type;

    @Override
    public String toString() {
        if (type.getSort() == Type.METHOD) {
            return name + type;
        } else {
            return name + ':' + type;
        }
    }
}
