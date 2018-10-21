package at.yawk.valda.ir.code;

import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class UnaryOperationTest {
    @DataProvider
    private Object[][] types() {
        return Arrays.stream(UnaryOperation.Type.values())
                .map(t -> new Object[]{ t })
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "types")
    public void typeSane(UnaryOperation.Type type) {
        if (type.name().endsWith("LONG") || type.name().endsWith("DOUBLE")) {
            Assert.assertEquals(type.getOutType(), LocalVariable.Type.WIDE);
        } else {
            Assert.assertEquals(type.getOutType(), LocalVariable.Type.NARROW);
        }
        if (type.name().startsWith("LONG") ||
            type.name().startsWith("DOUBLE")) {
            Assert.assertEquals(type.getOperandType(), LocalVariable.Type.WIDE);
        } else if (type.name().contains("INT") ||
                   type.name().contains("FLOAT") ||
                   type.name().contains("CHAR") ||
                   type.name().contains("SHORT") ||
                   type.name().contains("BYTE")) {
            Assert.assertEquals(type.getOperandType(), LocalVariable.Type.NARROW);
        } else {
            Assert.assertEquals(type.getOperandType(), LocalVariable.Type.WIDE);
        }
    }
}