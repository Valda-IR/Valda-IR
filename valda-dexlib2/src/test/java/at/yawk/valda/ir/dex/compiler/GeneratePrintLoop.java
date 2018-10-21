package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.LocalClassMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.GoTo;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.Return;
import java.io.IOException;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.writer.pool.DexPool;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
public final class GeneratePrintLoop {
    public static void main(String[] args) throws IOException {
        Classpath classpath = new Classpath();

        // set up class and main method
        LocalClassMirror classMirror = classpath.createClass(Type.getObjectType("PrintLoop"));
        LocalMethodMirror main = classMirror.addMethod("main");
        main.setStatic(true);
        main.addParameter(classpath.getTypeMirror(Type.getType("[Ljava/lang/String;")));

        /*
         * generate code:
         *
         * int i = 0;
         * int limit = 100;
         * while (i < limit) {
         *     PrintStream out = System.out;
         *     out.println(i);
         *     i++;
         * }
         * return;
         */

        LocalVariable i = LocalVariable.narrow("i");
        LocalVariable limit = LocalVariable.narrow("limit");

        // required blocks - declare them early so we can do cross-references easily
        BasicBlock init = BasicBlock.create();
        BasicBlock loopCondition = BasicBlock.create();
        BasicBlock loopBody = BasicBlock.create();
        BasicBlock loopEnd = BasicBlock.create();

        init.addInstruction(Const.createNarrow(i, 0)); // i = 0
        init.addInstruction(Const.createNarrow(limit, 100)); // limit = 100
        init.addInstruction(GoTo.create(loopCondition));

        loopCondition.addInstruction(Branch.builder()
                                             .type(Branch.Type.LESS_THAN)
                                             .lhs(i)
                                             .rhs(limit)
                                             .branchTrue(loopBody)
                                             .branchFalse(loopEnd)
                                             .build());

        LocalVariable out = LocalVariable.reference("out");
        // out = System.out
        loopBody.addInstruction(
                LoadStore.load()
                        .field(classpath.getTypeMirror(Type.getType("Ljava/lang/System;"))
                                       .field("out", Type.getType("Ljava/io/PrintStream;"), TriState.TRUE))
                        .value(out)
                        .build());
        // out.println(i)
        loopBody.addInstruction(
                Invoke.builder()
                        .method(classpath.getTypeMirror(Type.getType("Ljava/io/PrintStream;"))
                                        .method("println", Type.getType("(I)V"), TriState.TRUE))
                        .parameter(out).parameter(i)
                        .build());
        // i++
        loopBody.addInstruction(LiteralBinaryOperation.builder()
                                        .type(LiteralBinaryOperation.Type.ADD)
                                        .destination(i)
                                        .lhs(i)
                                        .rhs((short) 1)
                                        .build());
        loopBody.addInstruction(GoTo.create(loopCondition));

        loopEnd.addInstruction(Return.create(null));

        main.setBody(new MethodBody(init));

        // generate dex
        DexCompiler compiler = new DexCompiler();
        DexFile dexFile = compiler.compile(classpath);
        DexPool.writeTo("PrintLoop.dex", dexFile);
    }
}
