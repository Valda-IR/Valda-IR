package at.yawk.valda.ir.dex.parser;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction;
import org.jf.dexlib2.iface.instruction.ThreeRegisterInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.instruction.VariableRegisterInstruction;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction12x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22b;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31i;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction51l;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class TypeCheckerTest {
    @DataProvider
    public Object[][] opcodes() {
        return Arrays.stream(Opcode.values())
                .filter(oc -> !oc.odexOnly())
                .filter(oc -> oc != Opcode.INVOKE_POLYMORPHIC && oc != Opcode.INVOKE_POLYMORPHIC_RANGE &&
                              oc != Opcode.INVOKE_CUSTOM && oc != Opcode.INVOKE_CUSTOM_RANGE &&
                              oc != Opcode.CONST_METHOD_HANDLE && oc != Opcode.CONST_METHOD_TYPE)
                .filter(oc -> !oc.format.isPayloadFormat)
                .map(oc -> new Object[]{ oc })
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "opcodes")
    public void opcodeImplemented(Opcode opcode) throws ClassNotFoundException {
        Format format = opcode.format;
        Class<?> instructionClass = Class.forName(
                "org.jf.dexlib2.iface.instruction.formats.Instruction" + format.name().substring("Format".length()));

        TypeChecker checker = new TypeChecker(new InstructionList(Collections.emptyList())) {
            @Override
            void expectType(int reg, @NonNull RegisterType type) {
            }

            @Override
            void aliasAB(boolean wide) {
                Assert.assertTrue(TwoRegisterInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void expectTypeA(@NonNull RegisterType type) {
                Assert.assertTrue(OneRegisterInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void expectTypeB(@NonNull RegisterType type) {
                Assert.assertTrue(TwoRegisterInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void expectTypeC(@NonNull RegisterType type) {
                Assert.assertTrue(ThreeRegisterInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void setType(int reg, @Nullable RegisterType type) {
            }

            @Override
            void setTypeA(@Nullable RegisterType type) {
                Assert.assertTrue(OneRegisterInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void moveObject() {
                Assert.assertTrue(TwoRegisterInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void visitNarrowConst() {
                Assert.assertTrue(NarrowLiteralInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void visitMethod(boolean instanceMethod) {
                Assert.assertTrue(ReferenceInstruction.class.isAssignableFrom(instructionClass));
                Assert.assertTrue(VariableRegisterInstruction.class.isAssignableFrom(instructionClass));
                Assert.assertTrue(FiveRegisterInstruction.class.isAssignableFrom(instructionClass) ||
                                  RegisterRangeInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void branch() {
                Assert.assertTrue(OffsetInstruction.class.isAssignableFrom(instructionClass));
            }

            @Override
            void branchSwitch() {
                Assert.assertTrue(OffsetInstruction.class.isAssignableFrom(instructionClass));
            }
        };
        checker.visitOpcode(opcode);
    }

    @Test
    public void emptyMethod() {
        InstructionList list = new InstructionList(Collections.singletonList(
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void unreachable() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction10t(Opcode.GOTO, 2),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertTrue(typeChecker.isReachable(0));
        Assert.assertFalse(typeChecker.isReachable(1));
        Assert.assertTrue(typeChecker.isReachable(2));
    }

    @Test
    public void simpleCatch() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("")),
                new ImmutableInstruction21c(Opcode.CONST_STRING, 1, new ImmutableStringReference("")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.addCatch(2, 4, 5); // around second const-string
        typeChecker.run();

        Assert.assertTrue(typeChecker.isReachable(0));
        Assert.assertTrue(typeChecker.isReachable(1));
        Assert.assertTrue(typeChecker.isReachable(2));
        Assert.assertTrue(typeChecker.isReachable(3));

        Assert.assertEquals(typeChecker.getRegisterInputTypes(0), IntObjectMaps.immutable.empty());
        Assert.assertEquals(typeChecker.getRegisterInputTypes(1),
                            IntObjectMaps.immutable.of(0, RegisterType.REFERENCE));
        Assert.assertEquals(typeChecker.getRegisterInputTypes(2),
                            IntObjectMaps.immutable.of(0, RegisterType.REFERENCE)
                                    .newWithKeyValue(1, RegisterType.REFERENCE));
        Assert.assertEquals(typeChecker.getRegisterInputTypes(3),
                            IntObjectMaps.immutable.of(0, RegisterType.REFERENCE));
    }

    @Test(expectedExceptions = DexVerifyException.class)
    public void expectDefined() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction12x(Opcode.MOVE, 0, 1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test(expectedExceptions = DexVerifyException.class)
    public void expectCorrectType() {
        // try to move a low half of a wide register pair
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction51l(Opcode.CONST_WIDE, 1, 123L),
                new ImmutableInstruction12x(Opcode.MOVE, 0, 1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test(expectedExceptions = DexVerifyException.class)
    public void expectCorrectTypeWide() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 1, 123),
                new ImmutableInstruction12x(Opcode.MOVE_WIDE, 0, 1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test(expectedExceptions = DexVerifyException.class)
    public void expectCorrectTypeWideHigh() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction51l(Opcode.CONST_WIDE, 1, 123L),
                new ImmutableInstruction31i(Opcode.CONST, 2, 123),
                new ImmutableInstruction12x(Opcode.MOVE_WIDE, 0, 1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void invokeNoArg() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, new ImmutableMethodReference(
                        "x", "x", Collections.emptyList(), "V")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test(expectedExceptions = DexVerifyException.class)
    public void invokeArgLengthCheck() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, new ImmutableMethodReference(
                        "x", "x", Collections.emptyList(), "V")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test(expectedExceptions = DexVerifyException.class)
    public void invokeArgTypeCheck() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, new ImmutableMethodReference(
                        "x", "x", Collections.singletonList("I"), "V")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void invokeRange() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                new ImmutableInstruction51l(Opcode.CONST_WIDE, 1, 0L),
                new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 3, new ImmutableMethodReference(
                        "x", "x", Arrays.asList("I", "D"), "V")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void invokeConstructor() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference("Lx;")),
                new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0, new ImmutableMethodReference(
                        "x", "<init>", Collections.emptyList(), "V")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void parameter() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction12x(Opcode.MOVE, 0, 1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.addParameter(1, RegisterType.NARROW);
        typeChecker.run();
    }

    @Test(expectedExceptions = DexVerifyException.class)
    public void backtrackToFail() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 1, 1),
                // this is valid at first...
                new ImmutableInstruction12x(Opcode.MOVE, 10, 1),

                new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, 11L),
                // but this goto breaks it
                new ImmutableInstruction10t(Opcode.GOTO, -6),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void backtrackToSucceed() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 1, 1),
                // this is valid at first...
                new ImmutableInstruction12x(Opcode.MOVE, 10, 1),

                // but this goto doesn't break it - we didn't change type
                new ImmutableInstruction10t(Opcode.GOTO, -1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void backtrackIntoDeadCode() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction10t(Opcode.GOTO, 2),
                new ImmutableInstruction10x(Opcode.NOP),
                new ImmutableInstruction10t(Opcode.GOTO, -1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
    }

    @Test
    public void singleBlock() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction10x(Opcode.NOP),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertEquals(ImmutableList.copyOf(typeChecker.getBlocks()),
                            Collections.singletonList(new BlockBounds(0, 2)));
    }

    @Test
    public void multiBlock() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction10x(Opcode.NOP),
                new ImmutableInstruction10t(Opcode.GOTO, -1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertEquals(ImmutableList.copyOf(typeChecker.getBlocks()),
                            Arrays.asList(new BlockBounds(0, 2),
                                          new BlockBounds(2, 1)));
    }

    @Test
    public void constZero() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                new ImmutableInstruction21t(Opcode.IF_EQZ, 0, 4),
                new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 1, 0),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutableInstruction12x(Opcode.MOVE, 1, 0),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertEquals(
                typeChecker.getRegisterInputTypes(3).get(1),
                RegisterType.REFERENCE
        );
        Assert.assertEquals(
                typeChecker.getRegisterInputTypes(5).get(1),
                RegisterType.NARROW
        );
    }

    @Test
    public void constZeroLoop() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                new ImmutableInstruction22b(Opcode.ADD_INT_LIT8, 0, 0, 1),
                new ImmutableInstruction10t(Opcode.GOTO, -2)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertEquals(
                typeChecker.getRegisterInputTypes(1).get(0),
                RegisterType.NARROW
        );
    }

    @Test
    public void constZeroOr() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                new ImmutableInstruction12x(Opcode.OR_INT_2ADDR, 0, 0),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertEquals(
                typeChecker.getRegisterInputTypes(2).get(0),
                RegisterType.NARROW
        );
    }

    @Test
    public void moveUninitialized() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference("Lx;")),
                new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 1, 0),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertEquals(
                typeChecker.getRegisterInputTypes(2).get(1),
                RegisterType.REFERENCE_UNINITIALIZED
        );
    }

    @Test
    public void moveUninitializedAndUseBoth() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference("Lx;")),
                new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 1, 0),
                new ImmutableInstruction3rc(Opcode.INVOKE_DIRECT_RANGE, 1, 1, new ImmutableMethodReference(
                        "Lx;", "<init>", Collections.emptyList(), "V")),
                new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 1, new ImmutableMethodReference(
                        "Lx;", "test", Collections.emptyList(), "V")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        Assert.assertEquals(
                typeChecker.getRegisterInputTypes(2).get(1),
                RegisterType.REFERENCE_UNINITIALIZED
        );
        Assert.assertEquals(
                typeChecker.getRegisterInputTypes(3).get(0),
                RegisterType.REFERENCE
        );
    }
}