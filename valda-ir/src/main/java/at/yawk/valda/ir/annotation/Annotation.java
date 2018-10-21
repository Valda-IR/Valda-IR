package at.yawk.valda.ir.annotation;

import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.TypeMirror;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * @author yawkat
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class Annotation extends AnnotationMember {
    private final TypeMirror type;
    private final Map<MethodMirror, AnnotationMember> values;
}
