package at.yawk.valda.ir;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashSet;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class SuperMemberScannerTest {
    @Test
    public void basic() {
        Classpath classpath = new Classpath();
        LocalClassMirror cl = classpath.createClass(Type.getType("LTest;"));
        LocalMethodMirror method = cl.addMethod("test");

        Assert.assertEquals(
                SuperMemberScanner.methods().findOverriddenMembers(method),
                Collections.emptyList()
        );
    }

    @Test
    public void externalSupertype() {
        Classpath classpath = new Classpath();
        TypeMirror ext = classpath.getTypeMirror(Type.getType("Lext;"));
        LocalClassMirror cl = classpath.createClass(Type.getType("LTest;"));
        cl.setSuperType(ext);
        LocalMethodMirror method = cl.addMethod("test");

        Assert.assertEquals(
                SuperMemberScanner.methods().findOverriddenMembers(method),
                Collections.singletonList(ext)
        );
    }

    @Test
    public void externalSupertypeAndInterface() {
        Classpath classpath = new Classpath();
        TypeMirror ext = classpath.getTypeMirror(Type.getType("Lext;"));
        TypeMirror itf = classpath.getTypeMirror(Type.getType("Litf;"));
        LocalClassMirror cl = classpath.createClass(Type.getType("LTest;"));
        cl.setSuperType(ext);
        cl.addInterface(itf);
        LocalMethodMirror method = cl.addMethod("test");

        Assert.assertEquals(
                new HashSet<>(SuperMemberScanner.methods().findOverriddenMembers(method)),
                ImmutableSet.of(ext, itf)
        );
    }

    @Test
    public void localSupertype() {
        Classpath classpath = new Classpath();
        TypeMirror ext = classpath.getTypeMirror(Type.getType("Lext;"));
        LocalClassMirror sup = classpath.createClass(Type.getType("LTest1;"));
        sup.setSuperType(ext);
        LocalClassMirror cl = classpath.createClass(Type.getType("LTest;"));
        cl.setSuperType(sup);
        LocalMethodMirror method = cl.addMethod("test");

        Assert.assertEquals(
                SuperMemberScanner.methods().findOverriddenMembers(method),
                Collections.singletonList(ext)
        );
    }

    @Test
    public void packageSupertypeShenanigans1() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("La.A;"));
        LocalClassMirror b = classpath.createClass(Type.getType("Lb.B;"));
        LocalClassMirror c = classpath.createClass(Type.getType("La.C;"));
        b.setSuperType(a);
        c.setSuperType(b);

        // C.x() overrides A.x(), but B.x() is independent.
        a.addMethod("x").setAccess(Access.DEFAULT);
        b.addMethod("x").setAccess(Access.DEFAULT);
        LocalMethodMirror cMethod = c.addMethod("x");
        cMethod.setAccess(Access.PUBLIC);

        Assert.assertEquals(
                SuperMemberScanner.methods().findOverriddenMembers(cMethod),
                Collections.singletonList(a)
        );
    }

    @Test
    public void packageSupertypeShenanigans2() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("La.A;"));
        LocalClassMirror b = classpath.createClass(Type.getType("Lb.B;"));
        LocalClassMirror c = classpath.createClass(Type.getType("La.C;"));
        b.setSuperType(a);
        c.setSuperType(b);

        // C.x() overrides A.x(), but B.x() is independent.
        a.addMethod("x").setAccess(Access.DEFAULT);
        b.addMethod("x").setAccess(Access.PUBLIC);
        LocalMethodMirror cMethod = c.addMethod("x");
        cMethod.setAccess(Access.PUBLIC);

        Assert.assertEquals(
                new HashSet<>(SuperMemberScanner.methods().findOverriddenMembers(cMethod)),
                ImmutableSet.of(a, b)
        );
    }

    @Test
    public void toStringOnItf() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        a.setInterface(true);
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        b.addInterface(a);

        a.addMethod("x").setAccess(Access.DEFAULT);

        Assert.assertEquals(
                ImmutableSet.copyOf(SuperMemberScanner.methods().findOverriddenMembers(
                        b, new SuperMemberScanner.PossibleMember(
                                new MemberSignature("toString", Type.getMethodType("()Ljava/lang/String;")), false))),
                ImmutableSet.of(a, classpath.getTypeMirror(Type.getType(Object.class)))
        );
    }

    @Test
    public void fieldOnItfAndClass() {
        Classpath classpath = new Classpath();
        LocalClassMirror itf = classpath.createClass(Type.getType("LItf;"));
        itf.setInterface(true);
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        b.setSuperType(a);
        b.addInterface(itf);

        itf.addField("x", classpath.getTypeMirror(Type.INT_TYPE)).setStatic(true);
        a.addField("x", classpath.getTypeMirror(Type.INT_TYPE)).setStatic(true);

        Assert.assertEquals(
                ImmutableSet.copyOf(SuperMemberScanner.fields().findOverriddenMembers(
                        b, new SuperMemberScanner.PossibleMember(new MemberSignature("x", Type.INT_TYPE), true))),
                ImmutableSet.of(itf)
        );
    }

    @Test
    public void fieldOnRecursiveItf() {
        Classpath classpath = new Classpath();
        LocalClassMirror itf1 = classpath.createClass(Type.getType("LItf1;"));
        LocalClassMirror itf2 = classpath.createClass(Type.getType("LItf2;"));
        LocalClassMirror itf3 = classpath.createClass(Type.getType("LItf3;"));
        itf1.setInterface(true);
        itf2.setInterface(true);
        itf3.setInterface(true);
        itf1.addInterface(itf2);
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        a.addInterface(itf1);
        a.addInterface(itf3);

        itf2.addField("x", classpath.getTypeMirror(Type.INT_TYPE)).setStatic(true);
        itf3.addField("x", classpath.getTypeMirror(Type.INT_TYPE)).setStatic(true);

        Assert.assertEquals(
                ImmutableSet.copyOf(SuperMemberScanner.fields().findOverriddenMembers(
                        a, new SuperMemberScanner.PossibleMember(new MemberSignature("x", Type.INT_TYPE), true))),
                ImmutableSet.of(itf1, itf3)
        );
    }

    @Test
    public void privateNotInherited() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        b.setSuperType(a);

        a.addMethod("x").setAccess(Access.PRIVATE);

        Assert.assertNull(
                SuperMemberScanner.methods().findOverriddenMembers(
                        b, new SuperMemberScanner.PossibleMember(
                                new MemberSignature("x", Type.getMethodType("()V")), true))
        );
    }

    @Test
    public void listMembers() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        LocalClassMirror c = classpath.createClass(Type.getType("LC;"));
        b.setSuperType(a);
        c.setSuperType(b);

        a.addField("x", classpath.getTypeMirror(Type.INT_TYPE));
        b.addField("y", classpath.getTypeMirror(Type.INT_TYPE));

        Assert.assertEquals(
                new HashSet<>(SuperMemberScanner.fields().findAllMembers(c)),
                ImmutableSet.of(
                        new SuperMemberScanner.PossibleMember(new MemberSignature("x", Type.INT_TYPE), false),
                        new SuperMemberScanner.PossibleMember(new MemberSignature("y", Type.INT_TYPE), false)
                )
        );
    }

    @Test
    public void declaredFieldNotInherited() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        b.setSuperType(a);

        a.addField("x", classpath.getTypeMirror(Type.INT_TYPE));
        b.addField("x", classpath.getTypeMirror(Type.INT_TYPE));

        Assert.assertEquals(
                SuperMemberScanner.fields().findOverriddenMembers(
                        b, new SuperMemberScanner.PossibleMember(new MemberSignature("x", Type.INT_TYPE), false)),
                Collections.emptyList()
        );
    }

    @Test
    public void privateMethodNotInherited() {
        Classpath classpath = new Classpath();
        LocalClassMirror a = classpath.createClass(Type.getType("LA;"));
        LocalClassMirror b = classpath.createClass(Type.getType("LB;"));
        b.setSuperType(a);

        a.addMethod("x").setAccess(Access.PRIVATE);
        b.addMethod("x").setStatic(true);

        Assert.assertNull(
                SuperMemberScanner.methods().findOverriddenMembers(
                        b, new SuperMemberScanner.PossibleMember(
                                new MemberSignature("x", Type.getMethodType("()V")), false))
        );
    }
}