package at.yawk.valda.ir;

import java.util.Arrays;
import java.util.Collections;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class LocalClassMirrorTest {
    @Test
    public void superFieldAccess() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"), a);

        a.addField("x", classpath.getTypeMirror(Type.INT_TYPE));

        LocalFieldMirror af = a.field("x", Type.INT_TYPE, TriState.FALSE);
        Assert.assertFalse(!af.isDeclared());
        LocalFieldMirror bf = b.field("x", Type.INT_TYPE, TriState.FALSE);
        Assert.assertTrue(!bf.isDeclared());

        Assert.assertThrows(NoSuchMemberException.class, () -> b.field("y", Type.INT_TYPE, TriState.FALSE));
    }

    @Test
    public void superFieldAccessDefault() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"), a);

        a.addField("x", classpath.getTypeMirror(Type.INT_TYPE)).setAccess(Access.DEFAULT);

        LocalFieldMirror af = a.field("x", Type.INT_TYPE, TriState.FALSE);
        Assert.assertFalse(!af.isDeclared());
        LocalFieldMirror bf = b.field("x", Type.INT_TYPE, TriState.FALSE);
        Assert.assertTrue(!bf.isDeclared());

        Assert.assertThrows(NoSuchMemberException.class, () -> b.field("y", Type.INT_TYPE, TriState.FALSE));
    }

    @Test
    public void resolveAnnotationMethod() {
        Classpath classpath = new Classpath();
        LocalClassMirror classMirror = classpath.createClass(Type.getType("LSomeAnnotation;"));
        classMirror.setAnnotation(true);
        LocalMethodMirror method = classMirror.addMethod("test");
        method.setReturnType(classpath.getTypeMirror(Type.getType("LSomeEnum;")));

        Assert.assertSame(
                classMirror.annotationMethod("test", Collections.singletonList(Type.getType("LSomeEnum;"))),
                method);
        Assert.assertSame(
                classMirror.annotationMethod(
                        "test", Arrays.asList(Type.getType("Ljava/lang/Enum;"), Type.INT_TYPE)),
                method);
        Assert.assertThrows(
                NoSuchMemberException.class,
                () -> classMirror.annotationMethod("test", Collections.singletonList(Type.INT_TYPE)));
    }

    @Test
    public void superInterfaceFieldAccess() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        b.addInterface(a);

        a.addField("x", classpath.getTypeMirror(Type.INT_TYPE)).setStatic(true);

        LocalFieldMirror af = a.field("x", Type.INT_TYPE, TriState.TRUE);
        Assert.assertFalse(!af.isDeclared());
        LocalFieldMirror bf = b.field("x", Type.INT_TYPE, TriState.TRUE);
        Assert.assertTrue(!bf.isDeclared());

        Assert.assertThrows(NoSuchMemberException.class, () -> b.field("y", Type.INT_TYPE, TriState.FALSE));
    }
}