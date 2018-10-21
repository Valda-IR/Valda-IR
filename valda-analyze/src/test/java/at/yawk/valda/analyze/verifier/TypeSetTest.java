package at.yawk.valda.analyze.verifier;

import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class TypeSetTest {
    private static TypeSet.FlatTypeSet flatTypeSet(String... items) {
        TypeSet.FlatTypeSet fts = new TypeSet.FlatTypeSet();
        for (String type : items) {
            fts.add(Type.getType(type));
        }
        return fts;
    }

    @Test
    public void testFlatTypeSetUnion() {
        Assert.assertEquals(
                TypeSet.FlatTypeSet.union(flatTypeSet("LA;", "LC;", "LB;"), flatTypeSet("LC;", "LD;")),
                flatTypeSet("LA;", "LD;", "LC;", "LB;")
        );
    }

    @Test
    public void testFlatTypeSetIntersection() {
        Assert.assertEquals(
                TypeSet.FlatTypeSet.intersect(flatTypeSet("LA;", "LC;", "LB;"), flatTypeSet("LC;", "LD;")),
                flatTypeSet("LC;")
        );
    }

    @Test
    public void testFlatTypeSetContainsAll() {
        Assert.assertFalse(flatTypeSet("LA;", "LC;", "LB;").containsAll(flatTypeSet("LC;", "LD;")));
        Assert.assertTrue(flatTypeSet("LA;", "LC;", "LB;").containsAll(flatTypeSet("LC;", "LB;")));
    }

    @Test
    public void union() {
        Assert.assertEquals(
                TypeSet.union(
                        TypeSet.intersect(TypeSet.create(Type.getType("LA;")), TypeSet.create(Type.getType("LB;"))),
                        TypeSet.create(Type.getType("LA;"))
                ),
                TypeSet.create(Type.getType("LA;"))
        );
    }

    @Test
    public void intersection() {
        Assert.assertEquals(
                TypeSet.intersect(
                        TypeSet.intersect(TypeSet.create(Type.getType("LA;")), TypeSet.create(Type.getType("LB;"))),
                        TypeSet.create(Type.getType("LA;"))
                ),
                TypeSet.intersect(TypeSet.create(Type.getType("LA;")), TypeSet.create(Type.getType("LB;")))
        );
    }
}