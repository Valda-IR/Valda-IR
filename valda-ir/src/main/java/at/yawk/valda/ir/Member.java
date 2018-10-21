package at.yawk.valda.ir;

/**
 * @author yawkat
 */
public interface Member {
    String getName();

    MemberSignature getSignature();

    TypeMirror getDeclaringType();

    boolean isStatic();
}
