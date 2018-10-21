package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.Art;
import at.yawk.valda.SmaliUtils;
import at.yawk.valda.TestDexFileBuilder;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.dex.parser.DexParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.writer.pool.DexPool;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import org.zeroturnaround.exec.ProcessResult;

/**
 * @author yawkat
 */
@Slf4j
public class CompilerSymmetryIntegrationTest {
    @Test(dataProvider = "samples")
    public void test(Sample sample)
            throws IOException, TimeoutException, InterruptedException {
        log.info("Building smali");
        ProcessResult normalRun;
        DexFile normalFile;
        try (TestDexFileBuilder builder = new TestDexFileBuilder()) {
            for (@Language("smali") String smali : sample.smali) {
                builder.addSmali(smali);
            }
            Path dex = builder.buildFile();
            log.info("Running ART on normal dex");
            normalRun = Art.run(dex, sample.mainClass, sample.args);
            if (normalRun.getExitValue() != sample.exitValue) {
                Assert.fail("Normal run failed with exit code " + normalRun.getExitValue() + " and message: " +
                            normalRun.outputString());
            }
            log.info("Loading dex");
            normalFile = new DexBackedDexFile(Opcodes.getDefault(), Files.readAllBytes(dex));
        }

        log.info("Parsing dex");
        DexParser parser = new DexParser();
        parser.add(normalFile);
        Classpath classpath = parser.parse();

        log.info("Compiling dex");
        DexCompiler compiler = new DexCompiler();
        DexFile compiledFile = compiler.compile(classpath);

        if (sample.printBaksmali) {
            SmaliUtils.printBaksmali(compiledFile, s -> log.info("Baksmali: {}", s));
        }

        log.info("Writing dex");
        ProcessResult processedRun;
        Path outDex = Files.createTempFile("compiled-dex", ".dex");
        try {
            DexPool.writeTo(outDex.toString(), compiledFile);
            log.info("Running ART on recompiled dex");
            processedRun = Art.run(outDex, sample.mainClass, sample.args);
        } finally {
            Files.delete(outDex);
        }

        Assert.assertEquals(sanitizeOutput(processedRun.outputString()),
                            sanitizeOutput(normalRun.outputString()),
                            "output");
        if (sample.printOutput) {
            log.info("Output: {}", normalRun.outputString());
        }
        Assert.assertEquals(processedRun.getExitValue(), normalRun.getExitValue(), "exit value");
    }

    private static String sanitizeOutput(String s) {
        return s.replaceAll(
                "(dex2oat|dalvikvm) W .* method_verifier.cc:.*] Verification of .* took .*ms\r?\n",
                ""
        );
    }

    @DataProvider(parallel = true)
    public Object[][] samples() {
        List<Sample> samples = Lists.newArrayList(
                new Sample(
                        "empty class", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ""
                ).setExitValue(1),
                new Sample(
                        "empty main", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 1 " +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "invoke-static", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 1 " +
                        "   invoke-static {p0}, LTest;->a([Ljava/lang/String;)V " +
                        "   return-void " +
                        ".end method " +
                        ".method public static a([Ljava/lang/String;)V " +
                        "   .registers 1 " +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "invoke with repeating args", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 1 " +
                        "   array-length v0, p0" +
                        "   invoke-static {v0, v0, v0}, LTest;->a(III)V " +
                        "   return-void " +
                        ".end method " +
                        ".method public static a(III)V " +
                        "   .registers 3 " +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "return-object", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 1 " +
                        "   invoke-static {p0}, LTest;->a([Ljava/lang/String;)[Ljava/lang/String; " +
                        "   return-void " +
                        ".end method " +
                        ".method public static a([Ljava/lang/String;)[Ljava/lang/String; " +
                        "   .registers 1 " +
                        "   return-object p0 " +
                        ".end method "
                ),
                new Sample(
                        "move", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 2 " +
                        "   move-object v0, p0" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "smart move", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 1 " +
                        "   move-object p0, p0" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-4", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 2 " +
                        "   const p0, 5 " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, p0}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 2 " +
                        "   const p0, 123456789 " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, p0}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-16", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 2 " +
                        "   const p0, 1234 " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, p0}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-high-16", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 2 " +
                        "   const p0, 0x1200000 " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, p0}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-wide", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const-wide v1, 5.234 " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1, v2}, Ljava/io/PrintStream;->println(D)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-wide-16", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const-wide v1, 12345L " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1, v2}, Ljava/io/PrintStream;->println(D)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-wide-32", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const-wide v1, 1234567890L " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1, v2}, Ljava/io/PrintStream;->println(D)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-wide-high16", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const-wide v1, 0x1000000000000L " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1, v2}, Ljava/io/PrintStream;->println(D)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-string", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const-string v1, \"abc\" " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "const-class", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const-class v1, Ljava/lang/Object; " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "monitors", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const-class v1, Ljava/lang/Object; " +
                        "   monitor-enter v1 " +
                        "   invoke-static {v1}, Ljava/lang/Thread;->holdsLock(Ljava/lang/Object;)Z " +
                        "   move-result v0" +
                        "   if-eqz v0, :fail " +
                        "   monitor-exit v1 " +
                        "   return-void " +
                        "   :fail " +
                        "   const v0, 0" +
                        "   throw v0 " +
                        ".end method "
                ),
                new Sample(
                        "cast", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   invoke-static {p0}, LTest;->a(Ljava/lang/Object;)V" +
                        "   return-void " +
                        ".end method " +
                        ".method public static a(Ljava/lang/Object;)V" +
                        "   .registers 3 " +
                        "   check-cast p0, [Ljava/lang/String;" +
                        "   invoke-static {p0}, LTest;->b([Ljava/lang/String;)V" +
                        "   return-void " +
                        ".end method " +
                        ".method public static b([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "instance-of", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   instance-of v0, p0, [Ljava/lang/CharSequence;" +
                        "   if-eqz v0, :false" +
                        "   const v1, 1 " +
                        "   goto :end " +
                        "   :false const v1, 0 " +
                        "   :end" +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Z)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "array-length", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   array-length v1, p0 " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "new-array length", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3 " +
                        "   const v1, 5" +
                        "   new-array v1, v1, [C" +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println([C)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "new-array filled", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v1, 5" +
                        "   filled-new-array {v1}, [I" +
                        "   move-result-object v2 " +
                        "   new-instance v1, Ljava/lang/String;" +
                        "   const v3, 0" +
                        "   const v4, 1" +
                        "   invoke-direct {v1, v2, v3, v4}, Ljava/lang/String;-><init>([III)V" +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "fill-array-data", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v1, 15" +
                        "   new-array v1, v1, [C" +
                        "   fill-array-data v1, :data " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println([C)V" +
                        "   return-void" +
                        "   :data .array-data 2 " +
                        "       'H' 'e' 'l' 'l' 'o' ' ' 'W' 'o' 'r' 'l' 'd'" +
                        "   .end array-data " +
                        ".end method "
                ),
                new Sample(
                        "packed-switch", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v1, 15" +
                        "   packed-switch v1, :payload " +
                        "   const v1, 3 " +
                        "   :test1 const v1, 1 " +
                        "   :test2 const v1, 2 " +
                        "   return-void " +
                        "   :payload .packed-switch 1" +
                        "       :test1" +
                        "       :test2" +
                        "   .end packed-switch" +
                        ".end method "
                ),
                new Sample(
                        "sparse-switch", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v1, 15" +
                        "   sparse-switch v1, :payload " +
                        "   const v1, 3 " +
                        "   :test1 const v1, 1 " +
                        "   :test2 const v1, 2 " +
                        "   return-void " +
                        "   :payload .sparse-switch" +
                        "       1 -> :test1" +
                        "       3 -> :test2" +
                        "   .end sparse-switch" +
                        ".end method "
                ),
                new Sample(
                        "binary operation", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v0, 14" +
                        "   const v1, 15" +
                        "   add-int v2, v0, v1" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "binary operation in-place", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v0, 14" +
                        "   const v1, 15" +
                        "   add-int v0, v0, v1" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "binary operation in-place explicit", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v0, 14" +
                        "   const v1, 15" +
                        "   add-int/2addr v0, v1" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "binary operation literal 8", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v0, 14" +
                        "   add-int/lit8 v1, v0, 5" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "binary operation literal 16", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v0, 14" +
                        "   add-int/lit16 v1, v0, 5523" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "unary operation", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v0, 14" +
                        "   neg-int v1, v0" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "aput", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const-string v0, \"\"" +
                        "   const v1, 0" +
                        "   aput-object v0, p0, v1" +
                        "   return-void" +
                        ".end method "
                ).setArgs(new String[] {""}),
                new Sample(
                        "aget", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   const v1, 0" +
                        "   aget-object v0, p0, v1" +
                        "   return-void" +
                        ".end method "
                ).setArgs(new String[] {""}),
                new Sample(
                        "iget", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".field x:I " +
                        ".end field" +
                        ".method constructor <init>()V" +
                        "   .registers 1" +
                        "   invoke-direct {p0}, Ljava/lang/Object;-><init>()V " +
                        "   return-void " +
                        ".end method " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   new-instance v0, LTest;" +
                        "   invoke-direct {v0}, LTest;-><init>()V" +
                        "   iget v1, v0, LTest;->x:I" +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "iput", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".field x:I " +
                        ".end field" +
                        ".method constructor <init>()V" +
                        "   .registers 1" +
                        "   invoke-direct {p0}, Ljava/lang/Object;-><init>()V " +
                        "   return-void " +
                        ".end method " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   new-instance v0, LTest;" +
                        "   invoke-direct {v0}, LTest;-><init>()V" +
                        "   const v1, 5" +
                        "   iput v1, v0, LTest;->x:I" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "sget", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".field static x:I " +
                        ".end field" +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   sget v1, LTest;->x:I" +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "sget default value", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".field static x:I = 10 " +
                        ".end field" +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   sget v1, LTest;->x:I" +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(I)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "annotations", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 5 " +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        // class annotations
                        "   const-class v2, LX;" +
                        "   invoke-virtual {v2}, Ljava/lang/Class;->getAnnotations()" +
                        "[Ljava/lang/annotation/Annotation;" +
                        "   move-result-object v1" +
                        "   invoke-static {v1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;" +
                        "   move-result-object v1" +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V" +
                        // method annotations
                        "   invoke-virtual {v2}, Ljava/lang/Class;->getDeclaredMethods()[Ljava/lang/reflect/Method;" +
                        "   move-result-object v3" +
                        "   const v4, 0" +
                        "   aget-object v3, v3, v4" +
                        "   invoke-virtual {v3}, Ljava/lang/reflect/Method;->getAnnotations()" +
                        "[Ljava/lang/annotation/Annotation;" +
                        "   move-result-object v1" +
                        "   invoke-static {v1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;" +
                        "   move-result-object v1" +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V" +
                        // parameter annotations
                        "   invoke-virtual {v3}, Ljava/lang/reflect/Method;->getParameterAnnotations()" +
                        "[[Ljava/lang/annotation/Annotation;" +
                        "   move-result-object v3" +
                        "   const v4, 0" +
                        "   aget-object v1, v3, v4" +
                        "   invoke-static {v1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;" +
                        "   move-result-object v1" +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V" +
                        // field annotations
                        "   invoke-virtual {v2}, Ljava/lang/Class;->getDeclaredFields()[Ljava/lang/reflect/Field;" +
                        "   move-result-object v3" +
                        "   const v4, 0" +
                        "   aget-object v3, v3, v4" +
                        "   invoke-virtual {v3}, Ljava/lang/reflect/Field;->getAnnotations()" +
                        "[Ljava/lang/annotation/Annotation;" +
                        "   move-result-object v1" +
                        "   invoke-static {v1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;" +
                        "   move-result-object v1" +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/Object;)V" +
                        "   return-void" +
                        ".end method ",

                        ".class LX;" +
                        ".super Ljava/lang/Object; " +
                        ".annotation runtime LA;" +
                        "   string = \"abc\" " +
                        "   bool = true " +
                        "   byte = 0x5t " +
                        "   short = 0x5s " +
                        "   char = 'c' " +
                        "   int = 0x5 " +
                        "   long = 0x4L " +
                        "   float = 1.1F " +
                        "   double = 1.0D " +
                        "   class = LTest; " +
                        "   enum = .enum Ljava/lang/annotation/RetentionPolicy;" +
                        "->RUNTIME:Ljava/lang/annotation/RetentionPolicy; " +
                        "   annotation = .subannotation Ljava/lang/annotation/Target;" +
                        "   .end subannotation " +
                        "   array = { 0x5 } " +
                        ".end annotation" +
                        ".field public static x:I" +
                        "   .annotation runtime LA;" +
                        "      array = {} " + // empty array
                        "      class = null " + // null
                        "   .end annotation" +
                        ".end field" +
                        ".method public x(I)V" +
                        "   .annotation runtime LA;" +
                        "   .end annotation" +
                        "   .param p1" +
                        "       .annotation runtime LA;" +
                        "       .end annotation" +
                        "   .end param" +
                        "   .registers 2" +
                        "   return-void" +
                        ".end method",

                        ".class public annotation abstract interface LA;" +
                        ".super Ljava/lang/Object; " +
                        ".implements Ljava/lang/annotation/Annotation;" +
                        ".method public abstract string()Ljava/lang/String;" +
                        ".end method" +
                        ".method public abstract bool()Z" +
                        ".end method" +
                        ".method public abstract byte()B" +
                        ".end method" +
                        ".method public abstract short()S" +
                        ".end method" +
                        ".method public abstract char()C" +
                        ".end method" +
                        ".method public abstract int()I" +
                        ".end method" +
                        ".method public abstract long()J" +
                        ".end method" +
                        ".method public abstract float()F" +
                        ".end method" +
                        ".method public abstract double()D" +
                        ".end method" +
                        ".method public abstract class()Ljava/lang/Class;" +
                        ".end method" +
                        ".method public abstract enum()Ljava/lang/annotation/RetentionPolicy;" +
                        ".end method" +
                        ".method public abstract annotation()Ljava/lang/annotation/Target;" +
                        ".end method" +
                        ".method public abstract array()[I" +
                        ".end method"
                ),
                new Sample(
                        "more parameters than required registers", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3" +
                        "   const-wide v0, 0.0" +
                        "   new-instance v2, LTest;" +
                        "   invoke-direct {v2}, LTest;-><init>()V" +
                        "   invoke-virtual {v2, v0, v1, v0, v1}, LTest;->a(DD)V" +
                        "   return-void" +
                        ".end method " +
                        ".method public constructor <init>()V " +
                        "   .registers 1" +
                        "   invoke-direct {p0}, Ljava/lang/Object;-><init>()V " +
                        "   return-void " +
                        ".end method " +
                        ".method public a(DD)V " +
                        "   .registers 5" +
                        "   return-void " +
                        ".end method "
                ),
                new Sample(
                        "try exception", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3" +
                        "   :start" +
                        "   const v0, 0" +
                        "   throw v0" +
                        "   :end" +
                        "   return-void" +
                        "   .catchall {:start .. :end} :catch " +
                        "   :catch" +
                        "   move-exception v0" +
                        "   invoke-virtual {v0}, Ljava/lang/Throwable;->toString()Ljava/lang/String;" +
                        "   move-result-object v1" +
                        "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                        "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V" +
                        "   return-void" +
                        ".end method "
                ),
                new Sample(
                        "invoke-interface", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 3" +
                        "   const v0, 0" +
                        "   :try invoke-interface {v0}, Ljava/lang/Runnable;->run()V" +
                        "   :catch return-void" +
                        "   .catch Ljava/lang/NullPointerException; {:try .. :catch} :catch" +
                        ".end method "
                ),
                new Sample(
                        "infinite loop", "Test",
                        ".class public LTest; " +
                        ".super Ljava/lang/Object; " +
                        ".method public static main([Ljava/lang/String;)V" +
                        "   .registers 1" +
                        "   const v0, 1" +
                        "   if-gt v0, v0, :start" +
                        "   return-void " +
                        // this loop is never entered but our compiler is too dumb to detect that so it's still
                        // compiled properly
                        "   :start" +
                        "   nop" +
                        "   goto :start" +
                        ".end method "
                )
        );
        for (String s : new String[]{ "gt", "ge", "lt", "le", "eq", "ne" }) {
            samples.add(new Sample(
                    "if-" + s, "Test",
                    ".class public LTest; " +
                    ".super Ljava/lang/Object; " +
                    ".method public static main([Ljava/lang/String;)V" +
                    "   .registers 5 " +
                    "   const v0, 14" +
                    "   const v1, 14" +
                    "   if-" + s + " v0, v1, :snd" +
                    "   const-string v1, \"false\"" +
                    "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                    "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V" +
                    "   return-void" +
                    "   :snd " +
                    "   const-string v1, \"true\"" +
                    "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                    "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V" +
                    "   return-void" +
                    ".end method "
            ));
            samples.add(new Sample(
                    "if-" + s + "z", "Test",
                    ".class public LTest; " +
                    ".super Ljava/lang/Object; " +
                    ".method public static main([Ljava/lang/String;)V" +
                    "   .registers 5 " +
                    "   const v0, 14" +
                    "   if-" + s + "z v0, :snd" +
                    "   const-string v1, \"false\"" +
                    "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                    "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V" +
                    "   return-void" +
                    "   :snd " +
                    "   const-string v1, \"true\"" +
                    "   sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream; " +
                    "   invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V" +
                    "   return-void" +
                    ".end method "
            ));
        }

        return samples.stream().map(s -> new Object[]{ s }).toArray(Object[][]::new);
    }

    @Setter
    @Accessors(chain = true)
    private static class Sample {
        final String name;
        final String mainClass;
        @Language("smali") final String[] smali;
        int exitValue = 0;
        boolean printBaksmali;
        boolean printOutput;
        String[] args = new String[0];

        Sample(String name, String mainClass, @Language("smali") String... smali) {
            this.name = name;
            this.mainClass = mainClass;
            this.smali = smali;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}