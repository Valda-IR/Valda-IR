package at.yawk.valda;

import com.google.common.io.MoreFiles;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.intellij.lang.annotations.Language;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

/**
 * @author yawkat
 */
public class TestDexFileBuilder implements Closeable {
    private final Path path;
    private final Path smaliPath;

    public TestDexFileBuilder() throws IOException {
        path = Files.createTempDirectory("smali-test");
        smaliPath = path.resolve("smali");
        Files.createDirectory(smaliPath);
    }

    public static byte[] buildArray(@Language("smali") String... smali) throws IOException {
        try (TestDexFileBuilder builder = new TestDexFileBuilder()) {
            for (@Language("smali") String s : smali) {
                builder.addSmali(s);
            }
            return builder.buildArray();
        }
    }

    public void addSmali(@Language("smali") String smali) throws IOException {
        Files.write(smaliPath.resolve(UUID.randomUUID() + ".smali"), smali.getBytes(StandardCharsets.UTF_8));
    }

    public Path buildFile() throws IOException {
        Path output = path.resolve(UUID.randomUUID() + ".dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = output.toString();
        if (!Smali.assemble(options, smaliPath.toString())) { throw new RuntimeException(); }
        return output;
    }

    public ByteBuffer buildBuffer() throws IOException {
        try (FileChannel channel = FileChannel.open(buildFile())) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }

    public byte[] buildArray() throws IOException {
        return Files.readAllBytes(buildFile());
    }

    @Override
    public void close() throws IOException {
        MoreFiles.deleteRecursively(path);
    }
}
