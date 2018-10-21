package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.MethodMirror;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@UtilityClass
final class References {
    static ImmutableMethodReference method(MethodMirror method) {
        Type type = method.getType();
        return new ImmutableMethodReference(
                method.getDeclaringType().getType().toString(),
                method.getName(),
                Arrays.stream(type.getArgumentTypes()).map(Type::getDescriptor).collect(Collectors.toList()),
                type.getReturnType().getDescriptor()
        );
    }
    static ImmutableFieldReference field(FieldMirror field) {
        return new ImmutableFieldReference(
                field.getDeclaringType().getType().toString(),
                field.getName(),
                field.getType().getType().getDescriptor()
        );
    }
}
