package at.yawk.valda;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import org.testng.SkipException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * @author yawkat
 */
@UtilityClass
public final class Art {
    private static final String PATH = System.getenv("ART_RUNTIME");

    public static boolean available() {
        return PATH != null;
    }

    public static ProcessResult run(Path dex, String mainClass, String... args)
            throws InterruptedException, TimeoutException, IOException {
        if (!available()) { throw new SkipException("ART_RUNTIME env variable not set"); }
        return new ProcessExecutor()
                .command(ImmutableList.<String>builder()
                                 .add(PATH, "-cp", dex.toString(), mainClass)
                                 .add(args)
                                 .build())
                .exitValueAny()
                .readOutput(true)
                .timeout(20, TimeUnit.SECONDS)
                .execute();
    }
}
