package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.SmaliUtils;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.LocalClassMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.Return;
import at.yawk.valda.ir.code.Try;
import at.yawk.valda.ir.dex.parser.DexParser;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction31i;
import org.jf.dexlib2.writer.io.DexDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
@Slf4j
public class NaiveCodeCompilerTest {
    // most of this is tested in CompilerSymmetryIntegrationTest instead

    @Test
    public void newInstanceWithReturnInSameLocal() throws IOException {
        Classpath classpath = new Classpath();
        LocalClassMirror clazz = classpath.createClass(Type.getType("LTest;"));
        LocalMethodMirror method = clazz.addMethod("test");
        method.setStatic(true);

        LocalVariable local = LocalVariable.reference("l");
        BasicBlock entryPoint = BasicBlock.create();
        entryPoint.addInstruction(Const.createString(local, "s"));
        entryPoint.addInstruction(
                Invoke.builder().newInstance()
                        .method(classpath.getTypeMirror(Type.getType("Ljava/lang/String;"))
                                        .method("<init>",
                                                Type.getMethodType("(Ljava/lang/String;)V"),
                                                TriState.FALSE))
                        .parameter(local).returnValue(local)
                        .build());
        entryPoint.addInstruction(Return.create(null));
        method.setBody(new MethodBody(entryPoint));

        DexFile file = new DexCompiler().compile(classpath);

        SmaliUtils.printBaksmali(file, s -> log.info("newInstanceWithReturnInSameLocal: {}", s));

        DexParser parser = new DexParser();
        parser.add(file);
        MethodBody body = ((LocalClassMirror) parser.parse().getTypeMirror(Type.getType("LTest;")))
                .method("test", Type.getMethodType("()V"), TriState.TRUE)
                .getBody();
        Assert.assertNotNull(body);
    }

    @Test
    public void branchBacktrack() throws IOException {
        Classpath classpath = new Classpath();
        LocalClassMirror clazz = classpath.createClass(Type.getType("LTest;"));
        LocalMethodMirror method = clazz.addMethod("test");
        method.setStatic(true);

        LocalVariable local = LocalVariable.narrow("l");
        BasicBlock entryPoint = BasicBlock.create();

        for (int i = 0; i < ((1 << 16) / 3); i++) {
            entryPoint.addInstruction(Const.createNarrow(local, 0x12345678 + i));
        }
        entryPoint.addInstruction(Branch.builder()
                                          .type(Branch.Type.EQUAL)
                                          .lhs(local)
                                          .rhs(null)
                                          .branchTrue(entryPoint)
                                          .branchFalse(entryPoint)
                                          .build());
        method.setBody(new MethodBody(entryPoint));

        DexFile file = new DexCompiler().compile(classpath);

        SmaliUtils.printBaksmali(file, s -> log.trace("branchBacktrack: {}", s));

        MethodImplementation impl =
                Iterables.getOnlyElement(Iterables.getOnlyElement(file.getClasses()).getDirectMethods())
                        .getImplementation();
        Assert.assertNotNull(impl);
        Instruction first = impl.getInstructions().iterator().next();
        Assert.assertTrue(first instanceof Instruction31i);
        Assert.assertEquals(((Instruction31i) first).getNarrowLiteral(), 0x12345678);
    }

    @Test
    public void exceptionHandlerRange() throws IOException {
        Classpath classpath = new Classpath();
        LocalClassMirror clazz = classpath.createClass(Type.getType("LTest;"));
        LocalMethodMirror method = clazz.addMethod("test");
        method.setStatic(true);

        LocalVariable local = LocalVariable.narrow("l");
        BasicBlock entryPoint = BasicBlock.create();

        for (int i = 0; i < ((1 << 16) / 3); i++) {
            entryPoint.addInstruction(Const.createNarrow(local, 0x12345678 + i));
            // division can throw exception, so the generator has to generate the try
            entryPoint.addInstruction(
                    BinaryOperation.builder()
                            .lhs(local).rhs(local).destination(local).type(BinaryOperation.Type.DIV_INT)
                            .build());
        }
        entryPoint.addInstruction(Return.createVoid());
        Try try_ = new Try();
        try_.addCatch(entryPoint);
        entryPoint.setTry(try_);

        method.setBody(new MethodBody(entryPoint));

        DexFile file = new DexCompiler().compile(classpath);

        SmaliUtils.printBaksmali(file, s -> log.info("exceptionHandlerRange: {}", s));

        MethodImplementation impl =
                Iterables.getOnlyElement(Iterables.getOnlyElement(file.getClasses()).getDirectMethods())
                        .getImplementation();
        Assert.assertNotNull(impl);
        Instruction first = impl.getInstructions().iterator().next();
        Assert.assertTrue(first instanceof Instruction31i);
        Assert.assertEquals(((Instruction31i) first).getNarrowLiteral(), 0x12345678);

        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(Iterables.getOnlyElement(file.getClasses()));
        pool.writeTo(new DexDataStore() {
            @Nonnull
            @Override
            public OutputStream outputAt(int offset) {
                return new NullOutputStream();
            }

            @Nonnull
            @Override
            public InputStream readAt(int offset) {
                return new NullInputStream(100000);
            }

            @Override
            public void close() throws IOException {
            }
        });
    }
}