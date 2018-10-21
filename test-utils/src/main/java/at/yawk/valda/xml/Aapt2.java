package at.yawk.valda.xml;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.collections.impl.block.function.checked.ThrowingFunction;
import org.intellij.lang.annotations.Language;
import org.testng.SkipException;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * @author yawkat
 */
public final class Aapt2 {
    private static final String BUILD_TOOLS = System.getenv("ANDROID_BUILD_TOOLS");
    private static final String PLATFORM = System.getenv("ANDROID_PLATFORM");

    private final Map<String, String> files = new HashMap<>();
    private final String manifest;

    public static boolean available() {
        return BUILD_TOOLS != null && PLATFORM != null;
    }

    public static void checkAvailable() {
        if (!available()) { throw new SkipException("ANDROID_BUILD_TOOLS and/or ANDROID_PLATFORM env variable not set"); }
    }

    public Aapt2(@Language("xml") String manifest) {
        checkAvailable();
        this.manifest = manifest;
    }

    public Aapt2 xml(String fileName, @Language("xml") String xml) {
        files.put(fileName, xml);
        return this;
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public <R> R compile(ThrowingFunction<Path, R> access)
            throws Exception {
        Path tmp = Files.createTempDirectory(Aapt2.class.getName());
        try {
            Path inDir = tmp.resolve("in/res");
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path inFile = inDir.resolve(entry.getKey());
                Files.createDirectories(inFile.getParent());
                Files.write(inFile, entry.getValue().getBytes(StandardCharsets.UTF_8));
            }

            Path compiled = tmp.resolve("compile.zip");
            new ProcessExecutor()
                    .command(ImmutableList.<String>builder()
                                     .add(Paths.get(BUILD_TOOLS, "aapt2").toString(),
                                          "compile",
                                          "-o", compiled.toString(),
                                          "--dir", inDir.toString())
                                     .build())
                    .exitValueNormal()
                    .readOutput(true)
                    .timeout(20, TimeUnit.SECONDS)
                    .execute();

            Path manifest = tmp.resolve("AndroidManifest.xml");
            Files.write(manifest, this.manifest.getBytes(StandardCharsets.UTF_8));

            Path linked = tmp.resolve("link.apk");
            new ProcessExecutor()
                    .command(Paths.get(BUILD_TOOLS, "aapt2").toString(),
                             "link",
                             "-I", PLATFORM + "/android.jar",
                             "--manifest", manifest.toString(),
                             "-o", linked.toString(),
                             compiled.toString())
                    .exitValueNormal()
                    .readOutput(true)
                    .timeout(20, TimeUnit.SECONDS)
                    .execute();

            new ProcessExecutor()
                    .command(Paths.get(BUILD_TOOLS, "aapt2").toString(),
                             "dump",
                             linked.toString())
                    .exitValueNormal()
                    .redirectOutput(System.out)
                    .timeout(20, TimeUnit.SECONDS)
                    .execute();

            Files.walk(tmp).forEach(System.out::println);

            try (FileSystem zipFs = FileSystems.newFileSystem(linked, null)) {
                return access.safeValueOf(Iterables.getOnlyElement(zipFs.getRootDirectories()));
            }
        } finally {
            MoreFiles.deleteRecursively(tmp);
        }
    }
}
