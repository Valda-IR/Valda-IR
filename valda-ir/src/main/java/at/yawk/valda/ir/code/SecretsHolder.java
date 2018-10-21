package at.yawk.valda.ir.code;

import at.yawk.valda.ir.Secrets;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * @author yawkat
 */
@UtilityClass
public final class SecretsHolder {
    static Secrets secrets = null;

    static {
        Secrets.init();
    }

    public static void setSecrets(@NonNull Secrets secrets) {
        SecretsHolder.secrets = secrets;
    }
}
