package at.yawk.valda.ir;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class ExternalTypeMirrorTest {
    @Test
    public void hasAllObjectMethods() {
        Set<MemberSignature> signatures = new HashSet<>(ExternalTypeMirror.JAVA_LANG_OBJECT_METHODS);
        signatures.remove(new MemberSignature("<init>", Type.getMethodType("()V")));
        for (Method method : Object.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) { continue; }
            if (Modifier.isPrivate(method.getModifiers())) { continue; }

            MemberSignature signature = new MemberSignature(method.getName(), Type.getType(method));
            Assert.assertTrue(signatures.remove(signature), signature.toString());
        }
        Assert.assertTrue(signatures.isEmpty(), signatures.toString());
    }

    @Test
    public void resolveAnnotationMethod() {
        Classpath classpath = new Classpath();
        ExternalTypeMirror typeMirror = (ExternalTypeMirror) classpath.getTypeMirror(Type.getType("LX;"));

        // init with String, Enum and int
        ExternalMethodMirror method = typeMirror.annotationMethod(
                "test",
                ImmutableList.of(Type.getType(String.class), Type.getType(Enum.class), Type.getType(int.class)));
        Assert.assertThrows(IllegalStateException.class, method::getType);
        // Method is hard-coded *not* to be an enum type so this should be 100% incompatible
        Assert.assertThrows(NoSuchMemberException.class, () -> typeMirror.annotationMethod(
                "test",
                ImmutableList.of(Type.getType(Method.class))));
        Assert.assertSame(typeMirror.annotationMethod(
                "test", ImmutableList.of(Type.getType(String.class), Type.getType("LSomeEnum;"))), method);
        // now that int is gone getType should work
        Assert.assertEquals(method.getType(), Type.getMethodType("()Ljava/lang/Object;"));

        Assert.assertSame(typeMirror.annotationMethod(
                "test", ImmutableList.of(Type.getType("LSomeEnum;"))), method);
        Assert.assertEquals(method.getType(), Type.getMethodType("()LSomeEnum;"));
    }
}