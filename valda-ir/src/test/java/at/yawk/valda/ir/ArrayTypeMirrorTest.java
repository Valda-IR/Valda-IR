package at.yawk.valda.ir;

import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class ArrayTypeMirrorTest {
    @Test
    public void testMethods() {
        Classpath classpath = new Classpath();
        Type arrType = Type.getType("[I");
        ArrayTypeMirror mirror = (ArrayTypeMirror) classpath.getTypeMirror(arrType);

        Assert.assertThrows(NoSuchMemberException.class,
                            () -> mirror.field("length", Type.getType("I"), TriState.FALSE));
        Assert.assertThrows(NoSuchMemberException.class,
                            () -> mirror.method("x", Type.getType("I"), TriState.FALSE));
        for (MemberSignature method : ExternalTypeMirror.JAVA_LANG_OBJECT_METHODS) {
            mirror.method(method.getName(), method.getType(), TriState.FALSE);
        }
    }
}