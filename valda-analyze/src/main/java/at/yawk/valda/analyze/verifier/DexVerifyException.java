package at.yawk.valda.analyze.verifier;

/**
 * @author yawkat
 */
public final class DexVerifyException extends RuntimeException {
    public DexVerifyException(String message) {
        super(message);
    }
}
