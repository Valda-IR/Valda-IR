package at.yawk.valda.ir;

/**
 * @author yawkat
 */
public final class NoSuchMemberException extends RuntimeException {
    private final TypeMirror onType;
    private final MemberSignature signature;
    private final TriState isStatic;

    public NoSuchMemberException(String message, TypeMirror onType, MemberSignature signature, TriState isStatic) {
        super(message);
        this.onType = onType;
        this.signature = signature;
        this.isStatic = isStatic;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " onType=" + onType + " signature=" + signature + " isStatic=" + isStatic;
    }
}
