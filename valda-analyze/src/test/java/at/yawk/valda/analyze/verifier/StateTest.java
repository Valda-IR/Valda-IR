package at.yawk.valda.analyze.verifier;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class StateTest {
    @Test
    public void testRoundDown() {
        Assert.assertEquals(State.Narrow.roundDown(-1), -1);
        Assert.assertEquals(State.Narrow.roundDown(-2), -2);
        Assert.assertEquals(State.Narrow.roundDown(-3), -4);
        Assert.assertEquals(State.Narrow.roundDown(-5), -8);
        Assert.assertEquals(State.Narrow.roundDown(-134), -256);
        Assert.assertEquals(State.Narrow.roundDown(Integer.MIN_VALUE + 23), Integer.MIN_VALUE);
    }

    @Test
    public void testRoundUp() {
        Assert.assertEquals(State.Narrow.roundUp(1), 1);
        Assert.assertEquals(State.Narrow.roundUp(2), 3);
        Assert.assertEquals(State.Narrow.roundUp(4), 7);
        Assert.assertEquals(State.Narrow.roundUp(134), 255);
        Assert.assertEquals(State.Narrow.roundUp(Integer.MAX_VALUE - 12), Integer.MAX_VALUE);
    }
}