package at.yawk.valda.ir.code;

import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class BinaryOperationTest {
    @DataProvider
    private Object[][] types() {
        return Arrays.stream(BinaryOperation.Type.values())
                .map(t -> new Object[]{ t })
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "types")
    public void typeSane(BinaryOperation.Type type) {
        if (type.name().contains("LONG") || type.name().contains("DOUBLE")) {
            Assert.assertEquals(type.getLhsType(), LocalVariable.Type.WIDE);
            if (type.name().contains("SHR") || type.name().contains("USHR") || type.name().contains("SHL")) {
                Assert.assertEquals(type.getRhsType(), LocalVariable.Type.NARROW);
            } else {
                Assert.assertEquals(type.getRhsType(), LocalVariable.Type.WIDE);
            }
            if (type.name().startsWith("COMPARE")) {
                Assert.assertEquals(type.getOutType(), LocalVariable.Type.NARROW);
            } else {
                Assert.assertEquals(type.getOutType(), LocalVariable.Type.WIDE);
            }
        } else {
            Assert.assertEquals(type.getLhsType(), LocalVariable.Type.NARROW);
            Assert.assertEquals(type.getRhsType(), LocalVariable.Type.NARROW);
            Assert.assertEquals(type.getOutType(), LocalVariable.Type.NARROW);
        }
    }
}