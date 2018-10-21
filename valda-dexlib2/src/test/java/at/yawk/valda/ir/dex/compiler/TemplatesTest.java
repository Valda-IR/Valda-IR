package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.code.ArrayLength;
import at.yawk.valda.ir.code.ArrayLoadStore;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.FillArray;
import at.yawk.valda.ir.code.GoTo;
import at.yawk.valda.ir.code.InstanceOf;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.Monitor;
import at.yawk.valda.ir.code.Move;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Return;
import at.yawk.valda.ir.code.Switch;
import at.yawk.valda.ir.code.Throw;
import at.yawk.valda.ir.code.UnaryOperation;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class TemplatesTest {
    @DataProvider
    public Object[][] instructionClasses() {
        return new Object[][]{
                { ArrayLength.class },
                { ArrayLoadStore.class },
                { BinaryOperation.class },
                { Branch.class },
                { CheckCast.class },
                { Const.class },
                { FillArray.class },
                { GoTo.class },
                { InstanceOf.class },
                { Invoke.class },
                { LiteralBinaryOperation.class },
                { UnaryOperation.class },
                { LoadStore.class },
                { Monitor.class },
                { Move.class },
                { NewArray.class },
                { Return.class },
                { Switch.class },
                { Throw.class },
        };
    }

    @Test(dataProvider = "instructionClasses")
    public void classHasTemplate(Class<? extends Instruction> clazz) {
        for (InstructionTemplate<?> template : Templates.TEMPLATES) {
            if (template.type.equals(clazz)) {
                return;
            }
        }
        Assert.fail();
    }
}