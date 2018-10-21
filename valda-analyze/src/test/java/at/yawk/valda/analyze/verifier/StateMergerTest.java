package at.yawk.valda.analyze.verifier;

import at.yawk.valda.ir.code.BinaryOperation;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class StateMergerTest {
    @Test
    public void merge() {
        Assert.assertEquals(StateMerger.merge(null, State.OfType.FLOAT, State.Narrow.forValue(1234)),
                            State.OfType.FLOAT);
        Assert.assertEquals(StateMerger.merge(null, State.OfType.INT, State.Narrow.forValue(1234)),
                            State.OfType.INT);
        Assert.assertEquals(StateMerger.merge(null, State.OfType.SHORT, State.Narrow.forValue(1234)),
                            State.OfType.SHORT);
        Assert.assertEquals(StateMerger.merge(null, State.Narrow.forValue(23), State.Narrow.forValue(1234)),
                            new State.Narrow(23, 1234, true));
        Assert.assertEquals(StateMerger.merge(null, State.OfType.BYTE, State.Narrow.forValue(1234)),
                            State.OfType.SHORT);
        Assert.assertEquals(StateMerger.merge(null, State.OfType.BYTE, State.OfType.CHAR),
                            State.OfType.INT);
        // special case, could be char or short. see comment in the prod code.
        Assert.assertEquals(StateMerger.merge(null, State.OfType.BOOLEAN, State.Narrow.forValue(1234)),
                            new State.Narrow(0, 1234, true));


        Assert.assertEquals(StateMerger.merge(null, new State.OfType(Type.getType(String.class)), State.NULL),
                            new State.OfType(Type.getType(String.class)));
    }

    @Test
    public void mergeWithOperator() {
        Assert.assertEquals(
                StateMerger.mergeWithOp(null, State.OfType.INT, State.Narrow.forValue(1), BinaryOperation.Type.AND_INT),
                State.OfType.BOOLEAN
        );
        Assert.assertEquals(
                StateMerger.mergeWithOp(null, State.OfType.BYTE, State.Narrow.forValue(1), BinaryOperation.Type.AND_INT),
                State.OfType.BOOLEAN
        );
        Assert.assertEquals(
                StateMerger.mergeWithOp(null, State.OfType.BOOLEAN, State.Narrow.BOOLEAN, BinaryOperation.Type.OR_INT),
                State.OfType.BOOLEAN
        );
    }

    @Test
    public void mergeUnequalNarrowOperator() {
        Assert.assertEquals(
                StateMerger.mergeWithOp(null, State.OfType.BOOLEAN, State.OfType.BYTE, BinaryOperation.Type.MUL_INT),
                State.OfType.INT
        );
    }
}