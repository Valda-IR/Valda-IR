package at.yawk.valda.ir;

import java.io.Serializable;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class TypeMirrorsTest {
    @Test
    public void simple() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.FALSE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
        b.setSuperType(a);
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.TRUE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
    }

    @Test
    public void interface_() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        a.setInterface(true);
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.FALSE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
        b.addInterface(a);
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.TRUE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
    }

    @Test
    public void externalInterface() {
        Classpath classpath = new Classpath();
        TypeMirror a = classpath.getTypeMirror(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.FALSE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
        b.addInterface(a);
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.TRUE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
    }

    @Test
    public void externalInterfaceTransitive() {
        Classpath classpath = new Classpath();
        TypeMirror a = classpath.getTypeMirror(Type.getType("LA;"));
        TypeMirror c = classpath.getTypeMirror(Type.getType("LC;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.FALSE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
        b.addInterface(c);
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.MAYBE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
    }

    @Test
    public void externalSupertypeTransitive() {
        Classpath classpath = new Classpath();
        TypeMirror a = classpath.getTypeMirror(Type.getType("LA;"));
        TypeMirror c = classpath.getTypeMirror(Type.getType("LC;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.FALSE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
        b.setSuperType(c);
        Assert.assertEquals(TypeMirrors.isSupertype(a, b), TriState.MAYBE);
        Assert.assertEquals(TypeMirrors.isSupertype(b, a), TriState.FALSE);
    }

    @Test
    public void arrayNormal() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        Assert.assertEquals(TypeMirrors.isSupertype(a.getArrayType(), b.getArrayType()), TriState.FALSE);
        Assert.assertEquals(TypeMirrors.isSupertype(b.getArrayType(), a.getArrayType()), TriState.FALSE);
        b.setSuperType(a);
        Assert.assertEquals(TypeMirrors.isSupertype(a.getArrayType(), b.getArrayType()), TriState.TRUE);
        Assert.assertEquals(TypeMirrors.isSupertype(b.getArrayType(), a.getArrayType()), TriState.FALSE);
    }

    @Test
    public void arraySpecial() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        Assert.assertEquals(TypeMirrors.isSupertype(
                classpath.getTypeMirror(Type.getType(Serializable.class)), a.getArrayType()), TriState.TRUE);
        Assert.assertEquals(TypeMirrors.isSupertype(
                classpath.getTypeMirror(Type.getType(Cloneable.class)), a.getArrayType()), TriState.TRUE);
        Assert.assertEquals(TypeMirrors.isSupertype(
                classpath.getTypeMirror(Type.getType(Object.class)), a.getArrayType()), TriState.TRUE);
    }

    @Test
    public void primitiveArray() {
        Classpath classpath = new Classpath();
        Assert.assertEquals(TypeMirrors.isSupertype(
                classpath.getTypeMirror(Type.getType(Object[].class)),
                classpath.getTypeMirror(Type.getType(byte[].class))), TriState.FALSE);
        Assert.assertEquals(TypeMirrors.isSupertype(
                classpath.getTypeMirror(Type.getType(byte[].class)),
                classpath.getTypeMirror(Type.getType(Object[].class))
        ), TriState.FALSE);
    }
}