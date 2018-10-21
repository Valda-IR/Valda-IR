package at.yawk.valda;

import java.io.IOException;
import java.io.StringWriter;
import java.util.function.Consumer;
import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.util.IndentingWriter;

/**
 * @author yawkat
 */
public final class SmaliUtils {
    public static void printBaksmali(DexFile compiledFile, Consumer<String> print) throws IOException {
        BaksmaliOptions options = new BaksmaliOptions();
        for (ClassDef classDef : compiledFile.getClasses()) {
            ClassDefinition definition = new ClassDefinition(options, classDef);
            try (StringWriter writer = new StringWriter()) {
                definition.writeTo(new IndentingWriter(writer));
                print.accept(writer.toString());
            }
        }
    }
}
