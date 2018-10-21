package at.yawk.valda.ir.dex.parser;

import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.ExternalTypeMirror;
import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.FieldReference;
import at.yawk.valda.ir.LocalClassMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.MethodReference;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.TypeReference;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.GoTo;
import at.yawk.valda.ir.code.InstanceOf;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Return;
import at.yawk.valda.ir.code.Try;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.intellij.lang.annotations.Language;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class DexParserTest {
    private static DexFile assemble(@Language("smali") String smali) throws IOException {
        Path tmp = Files.createTempDirectory("DexParserTest");
        try {
            Path inSmali = tmp.resolve("smali");
            Files.write(inSmali, smali.getBytes(StandardCharsets.UTF_8));
            Path outDex = tmp.resolve("dex");

            SmaliOptions options = new SmaliOptions();
            options.outputDexFile = outDex.toString();
            options.jobs = 1;
            Assert.assertTrue(Smali.assemble(options, inSmali.toString()));

            byte[] dexBytes = Files.readAllBytes(outDex);
            return new DexBackedDexFile(Opcodes.forApi(options.apiLevel), dexBytes);
        } finally {
            MoreFiles.deleteRecursively(tmp);
        }
    }

    @Test
    public void simpleAst() throws IOException {
        DexParser parser = new DexParser();
        parser.add(assemble(".class abstract LTest; " +
                            ".super Ljava/lang/Object; " +
                            ".method abstract a (I)V " +
                            ".end method " +
                            ".field b:I " +
                            ".end field"));

        Classpath classpath = parser.parse();
        LocalClassMirror clazz = (LocalClassMirror) classpath.getTypeMirror(Type.getObjectType("Test"));
        Assert.assertTrue(clazz.getSuperType() instanceof ExternalTypeMirror);
        Assert.assertEquals(clazz.getSuperType().getType(), Type.getType(Object.class));

        Assert.assertEquals(clazz.getDeclaredMethods().size(), 1);
        LocalMethodMirror method = clazz.getDeclaredMethods().get(0);
        Assert.assertEquals(method.getName(), "a");
        Assert.assertNull(method.getReturnType());

        Assert.assertEquals(method.getParameters().size(), 1);
        LocalMethodMirror.Parameter parameter = method.getParameters().get(0);
        Assert.assertEquals(parameter.getType().getType(), Type.INT_TYPE);

        Assert.assertEquals(clazz.getDeclaredFields().size(), 1);
        FieldMirror field = clazz.getDeclaredFields().get(0);
        Assert.assertEquals(field.getName(), "b");
        Assert.assertEquals(field.getType().getType(), Type.INT_TYPE);
    }

    @Test
    public void returnVoid() throws IOException {
        DexParser parser = new DexParser();
        parser.add(assemble(".class abstract LTest; " +
                            ".super Ljava/lang/Object; " +
                            ".method a ()V " +
                            ".registers 1 " +
                            "return-void " +
                            ".end method"));

        Classpath classpath = parser.parse();
        LocalClassMirror clazz = (LocalClassMirror) classpath.getTypeMirror(Type.getObjectType("Test"));
        LocalMethodMirror method = clazz.getDeclaredMethods().get(0);
        MethodBody body = method.getBody();
        Assert.assertNotNull(body);

        Assert.assertEquals(
                body.getEntryPoint().getInstructions(),
                Collections.singletonList(
                        Return.create(null)
                )
        );
    }

    @Test
    public void references() throws IOException {
        DexParser parser = new DexParser();
        parser.add(assemble(".class abstract LTest; " +
                            ".super Ljava/lang/Object; " +
                            ".method a (LTest;)LTest; " +
                            ".registers 2 " +
                            ":start new-instance v0, LTest; " +
                            "invoke-direct {v0}, LTest;-><init>()V " +
                            "check-cast v0, LTest; " +
                            "instance-of v0, v0, LTest; " +
                            "new-array v0, v0, [LTest; " +
                            "iget-object v0, v0, LTest;->b:LTest; " +
                            "const-class v0, LTest; " +
                            ":catch return-void " +
                            "" +
                            ".catch LTest; {:start .. :catch} :catch" +
                            ".end method " +
                            "" +
                            ".method constructor <init> ()V " +
                            ".registers 1 " +
                            "return-void " +
                            ".end method" +
                            ".field b:LTest; " +
                            ".end field "
        ));

        Classpath classpath = parser.parse();
        LocalClassMirror clazz = (LocalClassMirror) classpath.getTypeMirror(Type.getObjectType("Test"));
        LocalMethodMirror method = clazz.method("a", Type.getType("(LTest;)LTest;"), TriState.FALSE);
        MethodBody body = method.getBody();
        Assert.assertNotNull(body);

        BasicBlock entryPoint = body.getEntryPoint();
        BasicBlock exit = ((GoTo) entryPoint.getTerminatingInstruction()).getTarget();

        Invoke constructorCall = Invoke.builder().newInstance()
                .method(clazz.method("<init>", Type.getType("()V"), TriState.FALSE))
                .returnValue(r(0, LocalVariable.Type.REFERENCE))
                .build();
        LoadStore load = LoadStore.load()
                .instance(r(0, LocalVariable.Type.REFERENCE))
                .field(clazz.getDeclaredFields().get(0))
                .value(r(0, LocalVariable.Type.REFERENCE))
                .build();
        InstanceOf instanceOf = InstanceOf.builder()
                .target(r(0, LocalVariable.Type.NARROW))
                .operand(r(0,
                           LocalVariable.Type.REFERENCE))
                .type(clazz)
                .build();
        NewArray newArray = NewArray.lengthBuilder()
                .target(r(0, LocalVariable.Type.REFERENCE))
                .type(clazz.getArrayType())
                .length(r(0, LocalVariable.Type.NARROW))
                .build();
        Const constClass = Const.createClass(r(0, LocalVariable.Type.REFERENCE), clazz);
        CheckCast cast = CheckCast.create(r(0, LocalVariable.Type.REFERENCE), clazz);
        Assert.assertEquals(
                entryPoint.getInstructions(),
                Arrays.asList(
                        constructorCall,
                        cast,
                        instanceOf,
                        newArray,
                        load,
                        constClass,
                        GoTo.create(exit)
                )
        );
        Try try_ = entryPoint.getTry();
        Assert.assertNotNull(try_);
        Assert.assertEquals(try_.getEnclosedBlocks(), Collections.singleton(entryPoint));
        Assert.assertEquals(try_.getHandlers().size(), 1);
        Assert.assertEquals(try_.getHandlers().get(0).getExceptionType(), clazz);
        Assert.assertEquals(try_.getHandlers().get(0).getHandler(), exit);

        // run this twice, first with the method body and then with the method body removed
        for (int i = 0; i < 2; i++) {
            if (i == 1) {
                method.setBody(null);
            }

            assertIterableAnyOrder(
                    clazz.getReferences().listReferences(TypeReference.Extends.class)
            );
            assertIterableAnyOrder(
                    clazz.getReferences().listReferences(TypeReference.ArrayComponentType.class),
                    ref -> Assert.assertEquals(ref.getArrayType().getComponentType(), clazz)
            );
            assertIterableAnyOrder(
                    clazz.getReferences().listReferences(TypeReference.MethodDeclaringType.class),
                    ref -> Assert.assertEquals(ref.getMethod(), method),
                    ref -> Assert.assertEquals(ref.getMethod(), constructorCall.getMethod())
            );
            assertIterableAnyOrder(
                    clazz.getReferences().listReferences(TypeReference.MethodReturnType.class),
                    ref -> Assert.assertEquals(ref.getMethod(), method)
            );
            assertIterableAnyOrder(
                    clazz.getReferences().listReferences(TypeReference.FieldDeclaringType.class),
                    ref -> Assert.assertEquals(ref.getField(), load.getField())
            );
            assertIterableAnyOrder(
                    clazz.getReferences().listReferences(TypeReference.FieldType.class),
                    ref -> Assert.assertEquals(ref.getField(), load.getField())
            );
            assertIterableAnyOrder(
                    clazz.getReferences().listReferences(TypeReference.ParameterType.class),
                    ref -> Assert.assertEquals(ref.getParameter(), method.getParameters().get(0))
            );
            if (i == 0) {
                assertIterableAnyOrder(
                        constructorCall.getMethod().getReferences().listReferences(MethodReference.Invoke.class),
                        invoke -> Assert.assertEquals(invoke.getInstruction(), constructorCall)
                );
                assertIterableAnyOrder(
                        load.getField().getReferences().listReferences(FieldReference.LoadStore.class),
                        invoke -> Assert.assertEquals(invoke.getInstruction(), load)
                );
                assertIterableAnyOrder(
                        clazz.getReferences().listReferences(TypeReference.InstanceOfType.class),
                        ref -> Assert.assertEquals(ref.getInstruction(), instanceOf)
                );
                assertIterableAnyOrder(
                        clazz.getArrayType().getReferences().listReferences(TypeReference.NewArrayType.class),
                        ref -> Assert.assertEquals(ref.getInstruction(), newArray)
                );
                assertIterableAnyOrder(
                        clazz.getReferences().listReferences(TypeReference.ConstClass.class),
                        ref -> Assert.assertEquals(ref.getInstruction(), constClass)
                );
                assertIterableAnyOrder(
                        clazz.getReferences().listReferences(TypeReference.Cast.class),
                        ref -> Assert.assertEquals(ref.getInstruction(), cast)
                );
                assertIterableAnyOrder(
                        clazz.getReferences().listReferences(TypeReference.CatchExceptionType.class),
                        ref -> Assert.assertEquals(ref.getCatch(), try_.getHandlers().get(0))
                );
            } else {
                Assert.assertEquals(constructorCall.getMethod()
                                            .getReferences()
                                            .listReferences(MethodReference.Invoke.class),
                                    ImmutableList.of());
                Assert.assertEquals(load.getField().getReferences().listReferences(FieldReference.LoadStore.class),
                                    ImmutableList.of());
                Assert.assertEquals(clazz.getReferences().listReferences(TypeReference.InstanceOfType.class),
                                    ImmutableList.of());
                Assert.assertEquals(clazz.getReferences().listReferences(TypeReference.NewArrayType.class),
                                    ImmutableList.of());
                Assert.assertEquals(clazz.getReferences().listReferences(TypeReference.ConstClass.class),
                                    ImmutableList.of());
                Assert.assertEquals(clazz.getReferences().listReferences(TypeReference.Cast.class),
                                    ImmutableList.of());
                Assert.assertEquals(clazz.getReferences().listReferences(TypeReference.CatchExceptionType.class),
                                    ImmutableList.of());
            }
        }
    }

    @SafeVarargs
    private static <T> void assertIterableAnyOrder(Iterable<T> iterable, Consumer<T>... consumers) {
        List<T> items = Lists.newArrayList(iterable);
        Assert.assertEquals(items.size(), consumers.length, "Mismatched items size: " + items);
        List<Consumer<T>> unmatched = new ArrayList<>();

        outer:
        for (Consumer<T> consumer : consumers) {
            for (Iterator<T> iterator = items.iterator(); iterator.hasNext(); ) {
                T item = iterator.next();
                try {
                    consumer.accept(item);
                    iterator.remove();
                    continue outer;
                } catch (AssertionError ignored) {
                }
            }
            unmatched.add(consumer);
        }

        assert items.size() == unmatched.size();
        if (!items.isEmpty()) {
            if (items.size() == 1) {
                // should fail again
                unmatched.get(0).accept(items.get(0));
                Assert.fail("wtf?");
            } else {
                Assert.fail("Unmatched items: " + items);
            }
        }
    }

    private static LocalVariable r(int index, LocalVariable.Type type) {
        return LocalVariable.create(type, "r" + index + "/" + type);
    }
}