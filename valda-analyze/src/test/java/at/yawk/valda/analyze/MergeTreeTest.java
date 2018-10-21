package at.yawk.valda.analyze;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class MergeTreeTest {
    @Test
    public void test() {
        MergeTree<Integer, String> tree = new MergeTree<>((a, b) -> a + b);
        tree.put(0, "a");
        tree.put(1, "b");
        Assert.assertEquals(tree.getMerged(), "ab");
        tree.put(2, "c");
        Assert.assertEquals(tree.getMerged(), "abc");
        tree.put(1, "x");
        Assert.assertEquals(tree.getMerged(), "axc");
    }

    @Test
    public void test2() {
        MergeTree<Integer, String> tree = new MergeTree<>((a, b) -> a + b);
        tree.put(1, "a");
        tree.put(2, "b");
        tree.put(3, "c");
        Assert.assertEquals(tree.getMerged(), "abc");
        tree.put(4, "d");
        Assert.assertEquals(tree.getMerged(), "abcd");
    }
}