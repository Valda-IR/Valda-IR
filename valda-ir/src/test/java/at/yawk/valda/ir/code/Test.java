package at.yawk.valda.ir.code;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.writer.builder.DexBuilder;

/**
 * @author yawkat
 */
public class Test {
    @org.testng.annotations.Test
    public void test() {
        DexBuilder builder = new DexBuilder(Opcodes.getDefault());

    }
}