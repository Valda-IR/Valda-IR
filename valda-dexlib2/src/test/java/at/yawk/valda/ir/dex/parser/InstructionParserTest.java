package at.yawk.valda.ir.dex.parser;

import at.yawk.valda.ir.ArrayTypeMirror;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.NoSuchMemberException;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.code.ArrayLength;
import at.yawk.valda.ir.code.ArrayLoadStore;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.FillArray;
import at.yawk.valda.ir.code.GoTo;
import at.yawk.valda.ir.code.InstanceOf;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.Monitor;
import at.yawk.valda.ir.code.Move;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Return;
import at.yawk.valda.ir.code.Switch;
import at.yawk.valda.ir.code.Throw;
import at.yawk.valda.ir.code.UnaryOperation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import org.eclipse.collections.impl.factory.primitive.ByteLists;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.factory.primitive.ShortLists;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.instruction.ImmutableArrayPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction12x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22b;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22s;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction23x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31i;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction51l;
import org.jf.dexlib2.immutable.instruction.ImmutablePackedSwitchPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableSparseSwitchPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableSwitchElement;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.objectweb.asm.Type;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class InstructionParserTest {
    private Classpath classpath;

    @BeforeTest
    public void init() {
        classpath = new Classpath();
    }

    @DataProvider
    public Object[][] codePieces() {

        List<Object[]> tests = Lists.newArrayList(new Object[][]{
                {
                        ImmutableList.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)),
                        ImmutableList.of(Return.createVoid())
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction10x(Opcode.NOP),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(Return.createVoid())
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                                new ImmutableInstruction12x(Opcode.MOVE, 1, 0),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                                Move.builder()
                                        .from(r(0, LocalVariable.Type.NARROW))
                                        .to(r(1, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, 12345L),
                                new ImmutableInstruction12x(Opcode.MOVE_WIDE, 1, 0),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createWide(r(0, LocalVariable.Type.WIDE), 12345L),
                                Move.builder()
                                        .from(r(0, LocalVariable.Type.WIDE))
                                        .to(r(1, LocalVariable.Type.WIDE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING,
                                                            0,
                                                            new ImmutableStringReference("test")),
                                new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 1, 0),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "test"),
                                Move.builder()
                                        .from(r(0, LocalVariable.Type.REFERENCE))
                                        .to(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                                new ImmutableInstruction11x(Opcode.RETURN, 0)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                                Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                                Return.create(r(0, LocalVariable.Type.NARROW))
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0,
                                                            new ImmutableStringReference("test")),
                                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "test"),
                                Return.create(r(0, LocalVariable.Type.REFERENCE))
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, 12345L),
                                new ImmutableInstruction11x(Opcode.RETURN_WIDE, 0)
                        ),
                        ImmutableList.of(
                                Const.createWide(r(0, LocalVariable.Type.WIDE), 12345L),
                                Return.create(r(0, LocalVariable.Type.WIDE))
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0,
                                                            new ImmutableStringReference("test")),
                                new ImmutableInstruction11x(Opcode.MONITOR_ENTER, 0),
                                new ImmutableInstruction11x(Opcode.MONITOR_EXIT, 0),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "test"),
                                Monitor.createEnter(r(0, LocalVariable.Type.REFERENCE)),
                                Monitor.createExit(r(0, LocalVariable.Type.REFERENCE)),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0,
                                                            new ImmutableStringReference("test")),
                                new ImmutableInstruction11x(Opcode.THROW, 0)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "test"),
                                Throw.create(r(0, LocalVariable.Type.REFERENCE))
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_CLASS, 0, new ImmutableTypeReference("Lx;")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createClass(r(0, LocalVariable.Type.REFERENCE), type("Lx;")),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0,
                                                            new ImmutableStringReference("abc")),
                                new ImmutableInstruction21c(Opcode.CHECK_CAST, 0, new ImmutableTypeReference("Lx;")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "abc"),
                                CheckCast.create(r(0, LocalVariable.Type.REFERENCE), type("Lx;")),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0,
                                                            new ImmutableStringReference("abc")),
                                new ImmutableInstruction22c(Opcode.INSTANCE_OF, 1, 0,
                                                            new ImmutableTypeReference("Lx;")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "abc"),
                                InstanceOf.builder()
                                        .target(r(1, LocalVariable.Type.NARROW))
                                        .operand(r(0,
                                                   LocalVariable.Type.REFERENCE))
                                        .type(type("Lx;"))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                                new ImmutableInstruction22c(Opcode.NEW_ARRAY, 1, 0,
                                                            new ImmutableTypeReference("[I")),
                                new ImmutableInstruction12x(Opcode.ARRAY_LENGTH, 2, 1),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                                Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                                NewArray.lengthBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[I"))
                                        .length(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                ArrayLength.builder()
                                        .target(r(2, LocalVariable.Type.NARROW))
                                        .operand(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                                new ImmutableInstruction35c(Opcode.FILLED_NEW_ARRAY, 1, 0, 0, 0, 0, 0,
                                                            new ImmutableTypeReference("[I")),
                                new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                                Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                                NewArray.variableBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[I"))
                                        .variable(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                                new ImmutableInstruction3rc(Opcode.FILLED_NEW_ARRAY_RANGE, 0, 1,
                                                            new ImmutableTypeReference("[I")),
                                new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                                Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                                NewArray.variableBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[I"))
                                        .variable(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 5),
                                new ImmutableInstruction22c(Opcode.NEW_ARRAY, 1, 0,
                                                            new ImmutableTypeReference("[I")),
                                new ImmutableInstruction31t(Opcode.FILL_ARRAY_DATA, 1, 4),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                                new ImmutableArrayPayload(4, ImmutableList.of(1, 2, 3))
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 5),
                                NewArray.lengthBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[I"))
                                        .length(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                FillArray.create(r(1, LocalVariable.Type.REFERENCE), IntLists.immutable.of(1, 2, 3)),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 5),
                                new ImmutableInstruction22c(Opcode.NEW_ARRAY, 1, 0,
                                                            new ImmutableTypeReference("[B")),
                                new ImmutableInstruction31t(Opcode.FILL_ARRAY_DATA, 1, 4),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                                new ImmutableArrayPayload(1, ImmutableList.of((byte) 1, (byte) 2, (byte) 3))
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 5),
                                NewArray.lengthBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[B"))
                                        .length(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                FillArray.create(r(1, LocalVariable.Type.REFERENCE),
                                                 ByteLists.immutable.of((byte) 1, (byte) 2, (byte) 3)),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 5),
                                new ImmutableInstruction22c(Opcode.NEW_ARRAY, 1, 0,
                                                            new ImmutableTypeReference("[S")),
                                new ImmutableInstruction31t(Opcode.FILL_ARRAY_DATA, 1, 4),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                                new ImmutableArrayPayload(2, ImmutableList.of((short) 1, (short) 2, (short) 3))
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 5),
                                NewArray.lengthBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[S"))
                                        .length(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                FillArray.create(r(1, LocalVariable.Type.REFERENCE),
                                                 ShortLists.immutable.of((short) 1, (short) 2, (short) 3)),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 5),
                                new ImmutableInstruction22c(Opcode.NEW_ARRAY, 1, 0,
                                                            new ImmutableTypeReference("[J")),
                                new ImmutableInstruction31t(Opcode.FILL_ARRAY_DATA, 1, 4),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                                new ImmutableArrayPayload(8, ImmutableList.of(1L, 2L, 3L))
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 5),
                                NewArray.lengthBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[J"))
                                        .length(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                FillArray.create(r(1, LocalVariable.Type.REFERENCE), LongLists.immutable.of(1, 2, 3)),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction22c(
                                        Opcode.IGET, 1, 0, new ImmutableFieldReference("Ljava/lang/String;", "x", "I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                LoadStore.load()
                                        .instance(r(0, LocalVariable.Type.REFERENCE))
                                        .field(field("Ljava/lang/String;", "x", "I", false))
                                        .value(r(1, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction31i(Opcode.CONST, 1, 123),
                                new ImmutableInstruction22c(
                                        Opcode.IPUT, 1, 0, new ImmutableFieldReference("Ljava/lang/String;", "x", "I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                Const.createNarrow(r(1, LocalVariable.Type.NARROW), 123),
                                LoadStore.store()
                                        .instance(r(0, LocalVariable.Type.REFERENCE))
                                        .field(field("Ljava/lang/String;", "x", "I", false))
                                        .value(r(1, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction22c(
                                        Opcode.IGET_WIDE, 1, 0,
                                        new ImmutableFieldReference("Ljava/lang/String;", "x", "D")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                LoadStore.load()
                                        .instance(r(0, LocalVariable.Type.REFERENCE))
                                        .field(field("Ljava/lang/String;", "x", "D", false))
                                        .value(r(1, LocalVariable.Type.WIDE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction51l(Opcode.CONST_WIDE, 1, 123L),
                                new ImmutableInstruction22c(
                                        Opcode.IPUT_WIDE, 1, 0,
                                        new ImmutableFieldReference("Ljava/lang/String;", "x", "D")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                Const.createWide(r(1, LocalVariable.Type.WIDE), 123L),
                                LoadStore.store()
                                        .instance(r(0, LocalVariable.Type.REFERENCE))
                                        .field(field("Ljava/lang/String;", "x", "D", false))
                                        .value(r(1, LocalVariable.Type.WIDE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction22c(
                                        Opcode.IGET_OBJECT, 1, 0,
                                        new ImmutableFieldReference("Ljava/lang/String;", "x", "[I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                LoadStore.load()
                                        .instance(r(0, LocalVariable.Type.REFERENCE))
                                        .field(field("Ljava/lang/String;", "x", "[I", false))
                                        .value(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction3rc(Opcode.FILLED_NEW_ARRAY_RANGE, 0, 0,
                                                            new ImmutableTypeReference("[I")),
                                new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1),
                                new ImmutableInstruction22c(
                                        Opcode.IPUT_OBJECT, 1, 0,
                                        new ImmutableFieldReference("Ljava/lang/String;", "x", "[I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                NewArray.variableBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[I"))
                                        .build(),
                                LoadStore.store()
                                        .instance(r(0, LocalVariable.Type.REFERENCE))
                                        .field(field("Ljava/lang/String;", "x", "[I", false))
                                        .value(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(
                                        Opcode.SGET, 1, new ImmutableFieldReference("Ljava/lang/String;", "sx", "I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                LoadStore.load()
                                        .field(field("Ljava/lang/String;", "sx", "I", true))
                                        .value(r(1, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 1, 123),
                                new ImmutableInstruction21c(
                                        Opcode.SPUT, 1, new ImmutableFieldReference("Ljava/lang/String;", "sx", "I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(1, LocalVariable.Type.NARROW), 123),
                                LoadStore.store()
                                        .field(field("Ljava/lang/String;", "sx", "I", true))
                                        .value(r(1, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(
                                        Opcode.SGET_WIDE, 1,
                                        new ImmutableFieldReference("Ljava/lang/String;", "sx", "D")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                LoadStore.load()
                                        .field(field("Ljava/lang/String;", "sx", "D", true))
                                        .value(r(1, LocalVariable.Type.WIDE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction51l(Opcode.CONST_WIDE, 1, 123L),
                                new ImmutableInstruction21c(
                                        Opcode.SPUT_WIDE, 1,
                                        new ImmutableFieldReference("Ljava/lang/String;", "sx", "D")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createWide(r(1, LocalVariable.Type.WIDE), 123L),
                                LoadStore.store()
                                        .field(field("Ljava/lang/String;", "sx", "D", true))
                                        .value(r(1, LocalVariable.Type.WIDE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(
                                        Opcode.SGET_OBJECT, 1,
                                        new ImmutableFieldReference("Ljava/lang/String;", "sx", "[I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                LoadStore.load()
                                        .field(field("Ljava/lang/String;", "sx", "[I", true))
                                        .value(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction3rc(Opcode.FILLED_NEW_ARRAY_RANGE, 0, 0,
                                                            new ImmutableTypeReference("[I")),
                                new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1),
                                new ImmutableInstruction21c(
                                        Opcode.SPUT_OBJECT, 1,
                                        new ImmutableFieldReference("Ljava/lang/String;", "sx", "[I")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                NewArray.variableBuilder()
                                        .target(r(1, LocalVariable.Type.REFERENCE))
                                        .type((ArrayTypeMirror) type("[I"))
                                        .build(),
                                LoadStore.store()
                                        .field(field("Ljava/lang/String;", "sx", "[I", true))
                                        .value(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                                                            new ImmutableMethodReference(
                                                                    "Ljava/lang/String;", "test",
                                                                    ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                Invoke.builder().special()
                                        .method(method("Ljava/lang/String;", "test", "()V", false))
                                        .parameter(r(0, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction35c(Opcode.INVOKE_INTERFACE, 1, 0, 0, 0, 0, 0,
                                                            new ImmutableMethodReference(
                                                                    // different type for invoke-interface compatibility
                                                                    "Ljava/lang/NotString;", "test",
                                                                    ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                Invoke.builder()
                                        .method(method("Ljava/lang/NotString;", "test", "()V", false))
                                        .parameter(r(0, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
                                                            new ImmutableMethodReference(
                                                                    "Ljava/lang/String;", "test",
                                                                    ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                Invoke.builder()
                                        .method(method("Ljava/lang/String;", "test", "()V", false))
                                        .parameter(r(0, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference("ab")),
                                new ImmutableInstruction35c(Opcode.INVOKE_SUPER, 1, 0, 0, 0, 0, 0,
                                                            new ImmutableMethodReference(
                                                                    "Ljava/lang/String;", "test",
                                                                    ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createString(r(0, LocalVariable.Type.REFERENCE), "ab"),
                                Invoke.builder().special()
                                        .method(method("Ljava/lang/String;", "test", "()V", false))
                                        .parameter(r(0, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                                                            new ImmutableMethodReference(
                                                                    "Ljava/lang/String;", "stest",
                                                                    ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Invoke.builder()
                                        .method(method("Ljava/lang/String;", "stest", "()V", true))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference("Lx;")),
                                new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                                                            new ImmutableMethodReference("Lx;", "<init>",
                                                                                         ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Invoke.builder().newInstance()
                                        .method(method("Lx;", "<init>", "()V", false))
                                        .returnValue(r(0, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference("Lx;")),
                                new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 1, 0),
                                new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, 1, 0, 0, 0, 0,
                                                            new ImmutableMethodReference("Lx;", "<init>",
                                                                                         ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Invoke.builder().newInstance()
                                        .method(method("Lx;", "<init>", "()V", false))
                                        .returnValue(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Move.builder()
                                        .from(r(1, LocalVariable.Type.REFERENCE))
                                        .to(r(0, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference("Lx;")),
                                new ImmutableInstruction12x(Opcode.MOVE_OBJECT, 1, 0),
                                new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                                                            new ImmutableMethodReference("Lx;", "<init>",
                                                                                         ImmutableList.of(), "V")),
                                new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
                                                            new ImmutableMethodReference("Lx;", "x",
                                                                                         ImmutableList.of(), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Invoke.builder().newInstance()
                                        .method(method("Lx;", "<init>", "()V", false))
                                        .returnValue(r(0, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Move.builder()
                                        .from(r(0, LocalVariable.Type.REFERENCE))
                                        .to(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Invoke.builder()
                                        .method(method("Lx;", "x", "()V", false))
                                        .parameter(r(1, LocalVariable.Type.REFERENCE))
                                        .build(),
                                Return.createVoid()
                        )
                },
                {
                        ImmutableList.of(
                                new ImmutableInstruction31i(Opcode.CONST, 0, 56),
                                new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 1,
                                                            new ImmutableMethodReference(
                                                                    "Ljava/lang/String;", "stest",
                                                                    ImmutableList.of("I"), "V")),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 56),
                                Invoke.builder()
                                        .method(method("Ljava/lang/String;", "stest", "(I)V", true))
                                        .parameter(r(0, LocalVariable.Type.NARROW))
                                        .build(),
                                Return.createVoid()
                        )
                },
        });
        for (UnaryOperation.Type un : UnaryOperation.Type.values()) {
            Opcode opcode = Opcode.valueOf(un.name().replace("NEGATE", "NEG"));
            tests.add(new Object[]{
                    ImmutableList.of(
                            un.getOperandType() == LocalVariable.Type.NARROW ?
                                    new ImmutableInstruction31i(Opcode.CONST, 0, 1) :
                                    new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, 0),
                            new ImmutableInstruction12x(opcode, 1, 0),
                            new ImmutableInstruction10x(Opcode.RETURN_VOID)
                    ),
                    ImmutableList.of(
                            un.getOperandType() == LocalVariable.Type.NARROW ?
                                    Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1) :
                                    Const.createWide(r(0, LocalVariable.Type.WIDE), 0L),
                            UnaryOperation.builder()
                                    .type(un)
                                    .source(r(0, un.getOperandType()))
                                    .destination(r(1, un.getOutType()))
                                    .build(),
                            Return.createVoid()
                    )
            });
        }
        for (BinaryOperation.Type bi : BinaryOperation.Type.values()) {
            String dexlibName = bi.name();
            dexlibName = dexlibName.replaceAll("COMPARE_(.+)_BIAS_([LG])", "CMP$2_$1");
            dexlibName = dexlibName.replaceAll("COMPARE_(.+)", "CMP_$1");
            Opcode tri = Opcode.valueOf(dexlibName);
            tests.add(new Object[]{
                    ImmutableList.of(
                            bi.getLhsType() == LocalVariable.Type.NARROW ?
                                    new ImmutableInstruction31i(Opcode.CONST, 0, 1) :
                                    new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, 0),
                            bi.getRhsType() == LocalVariable.Type.NARROW ?
                                    new ImmutableInstruction31i(Opcode.CONST, 2, 5) :
                                    new ImmutableInstruction51l(Opcode.CONST_WIDE, 2, 5),
                            new ImmutableInstruction23x(tri, 4, 0, 2),
                            new ImmutableInstruction10x(Opcode.RETURN_VOID)
                    ),
                    ImmutableList.of(
                            bi.getLhsType() == LocalVariable.Type.NARROW ?
                                    Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1) :
                                    Const.createWide(r(0, LocalVariable.Type.WIDE), 0L),
                            bi.getRhsType() == LocalVariable.Type.NARROW ?
                                    Const.createNarrow(r(2, LocalVariable.Type.NARROW), 5) :
                                    Const.createWide(r(2, LocalVariable.Type.WIDE), 5L),
                            BinaryOperation.builder()
                                    .type(bi)
                                    .destination(r(4, bi.getOutType()))
                                    .lhs(r(0, bi.getLhsType()))
                                    .rhs(r(2, bi.getRhsType()))
                                    .build(),
                            Return.createVoid()
                    )
            });
            Opcode inPlace;
            try {
                inPlace = Opcode.valueOf(dexlibName + "_2ADDR");
            } catch (IllegalArgumentException e) {
                inPlace = null;
            }
            if (inPlace != null) {
                tests.add(new Object[]{
                        ImmutableList.of(
                                bi.getLhsType() == LocalVariable.Type.NARROW ?
                                        new ImmutableInstruction31i(Opcode.CONST, 0, 1) :
                                        new ImmutableInstruction51l(Opcode.CONST_WIDE, 0, 0),
                                bi.getRhsType() == LocalVariable.Type.NARROW ?
                                        new ImmutableInstruction31i(Opcode.CONST, 2, 5) :
                                        new ImmutableInstruction51l(Opcode.CONST_WIDE, 2, 5),
                                new ImmutableInstruction12x(inPlace, 0, 2),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)
                        ),
                        ImmutableList.of(
                                bi.getLhsType() == LocalVariable.Type.NARROW ?
                                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1) :
                                        Const.createWide(r(0, LocalVariable.Type.WIDE), 0L),
                                bi.getRhsType() == LocalVariable.Type.NARROW ?
                                        Const.createNarrow(r(2, LocalVariable.Type.NARROW), 5) :
                                        Const.createWide(r(2, LocalVariable.Type.WIDE), 5L),
                                BinaryOperation.builder()
                                        .type(bi)
                                        .destination(r(0, bi.getOutType()))
                                        .lhs(r(0, bi.getLhsType()))
                                        .rhs(r(2, bi.getRhsType()))
                                        .build(),
                                Return.createVoid()
                        )
                });
            }
        }
        for (LiteralBinaryOperation.Type lbi : LiteralBinaryOperation.Type.values()) {
            tests.add(new Object[]{
                    ImmutableList.of(
                            new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                            new ImmutableInstruction22b(Opcode.valueOf(lbi.name() + "_INT_LIT8"), 1, 0, -1),
                            new ImmutableInstruction10x(Opcode.RETURN_VOID)
                    ),
                    ImmutableList.of(
                            Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                            Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                            LiteralBinaryOperation.builder().type(lbi).destination(r(1, LocalVariable.Type.NARROW)).lhs(
                                    r(0, LocalVariable.Type.NARROW)).rhs((short) -1).build(),
                            Return.createVoid()
                    )
            });
            if (lbi == LiteralBinaryOperation.Type.SHL || lbi == LiteralBinaryOperation.Type.SHR ||
                lbi == LiteralBinaryOperation.Type.USHR) { continue; }
            Opcode op16 = lbi == LiteralBinaryOperation.Type.RSUB ?
                    Opcode.RSUB_INT :
                    Opcode.valueOf(lbi.name() + "_INT_LIT16");
            tests.add(new Object[]{
                    ImmutableList.of(
                            new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                            new ImmutableInstruction22s(op16, 1, 0, 500),
                            new ImmutableInstruction10x(Opcode.RETURN_VOID)
                    ),
                    ImmutableList.of(
                            Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                            Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                            LiteralBinaryOperation.builder().type(lbi).destination(r(1, LocalVariable.Type.NARROW)).lhs(
                                    r(0, LocalVariable.Type.NARROW)).rhs((short) 500).build(),
                            Return.createVoid()
                    )
            });
        }
        for (ArrayLoadStore.ElementType elementType : ArrayLoadStore.ElementType.values()) {
            String type;
            Opcode put;
            Opcode get;
            LocalVariable.Type valueType;
            switch (elementType) {
                case BOOLEAN: {
                    type = "[Z";
                    put = Opcode.APUT_BOOLEAN;
                    get = Opcode.AGET_BOOLEAN;
                    valueType = LocalVariable.Type.NARROW;
                    break;
                }
                case BYTE: {
                    type = "[B";
                    put = Opcode.APUT_BYTE;
                    get = Opcode.AGET_BYTE;
                    valueType = LocalVariable.Type.NARROW;
                    break;
                }
                case SHORT: {
                    type = "[S";
                    put = Opcode.APUT_SHORT;
                    get = Opcode.AGET_SHORT;
                    valueType = LocalVariable.Type.NARROW;
                    break;
                }
                case CHAR: {
                    type = "[C";
                    put = Opcode.APUT_CHAR;
                    get = Opcode.AGET_CHAR;
                    valueType = LocalVariable.Type.NARROW;
                    break;
                }
                case INT_FLOAT: {
                    type = "[F";
                    put = Opcode.APUT;
                    get = Opcode.AGET;
                    valueType = LocalVariable.Type.NARROW;
                    break;
                }
                case WIDE: {
                    type = "[D";
                    put = Opcode.APUT_WIDE;
                    get = Opcode.AGET_WIDE;
                    valueType = LocalVariable.Type.WIDE;
                    break;
                }
                case REFERENCE: {
                    type = "[Ljava/lang/Object;";
                    put = Opcode.APUT_OBJECT;
                    get = Opcode.AGET_OBJECT;
                    valueType = LocalVariable.Type.REFERENCE;
                    break;
                }
                default:
                    throw new AssertionError();
            }
            tests.add(new Object[]{
                    ImmutableList.of(
                            new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                            new ImmutableInstruction22c(Opcode.NEW_ARRAY, 1, 0, new ImmutableTypeReference(type)),
                            new ImmutableInstruction23x(get, 2, 1, 0),
                            new ImmutableInstruction23x(put, 2, 1, 0),
                            new ImmutableInstruction10x(Opcode.RETURN_VOID)
                    ),
                    ImmutableList.of(
                            Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                            NewArray.lengthBuilder()
                                    .target(r(1, LocalVariable.Type.REFERENCE))
                                    .type((ArrayTypeMirror) type(type))
                                    .length(r(0, LocalVariable.Type.NARROW))
                                    .build(),
                            ArrayLoadStore.builder()
                                    .type(LoadStore.Type.LOAD).elementType(elementType)
                                    .array(r(1, LocalVariable.Type.REFERENCE)).index(r(0, LocalVariable.Type.NARROW))
                                    .value(r(2, valueType))
                                    .build(),
                            ArrayLoadStore.builder()
                                    .type(LoadStore.Type.STORE).elementType(elementType)
                                    .array(r(1, LocalVariable.Type.REFERENCE)).index(r(0, LocalVariable.Type.NARROW))
                                    .value(r(2, valueType))
                                    .build(),
                            Return.createVoid()
                    )
            });
        }
        return tests.toArray(new Object[0][]);
    }

    private static LocalVariable r(int index, LocalVariable.Type type) {
        return LocalVariable.create(type, "r" + index + "/" + type);
    }

    private TypeMirror type(String type) {
        return classpath.getTypeMirror(Type.getType(type));
    }

    private FieldMirror field(String declaring, String name, String type, boolean isStatic) {
        return classpath.getTypeMirror(Type.getType(declaring))
                .field(name, Type.getType(type), TriState.valueOf(isStatic));
    }

    private MethodMirror method(String declaring, String name, String type, boolean isStatic) {
        return classpath.getTypeMirror(Type.getType(declaring))
                .method(name, Type.getType(type), TriState.valueOf(isStatic));
    }

    @Test(dataProvider = "codePieces")
    public void test(List<Instruction> dex, List<at.yawk.valda.ir.code.Instruction> hl) {
        InstructionList list = new InstructionList(dex);
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);

        BasicBlock block = parser.run();
        //noinspection RedundantCast
        Assert.assertEquals(
                (Object) block.getInstructions(),
                (Object) hl
        );
    }

    @Test
    public void testSimpleLoop() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                new ImmutableInstruction10t(Opcode.GOTO, -3)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);

        BasicBlock block = parser.run();
        Assert.assertEquals(
                block.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                        GoTo.create(block)
                )
        );
        Assert.assertEquals(block.getTerminatingInstruction(), GoTo.create(block));
        Assert.assertNull(block.getTry());
    }

    @Test
    public void testIf() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                new ImmutableInstruction31i(Opcode.CONST, 1, 1),
                new ImmutableInstruction22t(Opcode.IF_EQ, 0, 1, -6),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);

        BasicBlock block = parser.run();
        BasicBlock retBlock = ((Branch) block.getTerminatingInstruction()).getBranchFalse();
        Assert.assertEquals(
                block.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                        Const.createNarrow(r(1, LocalVariable.Type.NARROW), 1),
                        Branch.builder()
                                .type(Branch.Type.EQUAL)
                                .lhs(r(0, LocalVariable.Type.NARROW))
                                .rhs(r(1,
                                       LocalVariable.Type.NARROW))
                                .branchTrue(block)
                                .branchFalse(retBlock)
                                .build()
                )
        );
        Assert.assertEquals(
                retBlock.getInstructions(),
                Arrays.asList(
                        Return.createVoid()
                )
        );
    }

    @Test
    public void testIfZero() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                new ImmutableInstruction21t(Opcode.IF_EQZ, 0, -3),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);

        BasicBlock block = parser.run();
        BasicBlock retBlock = ((Branch) block.getTerminatingInstruction()).getBranchFalse();
        Assert.assertEquals(
                block.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                        Branch.builder()
                                .type(Branch.Type.EQUAL)
                                .lhs(r(0, LocalVariable.Type.NARROW))
                                .rhs(null)
                                .branchTrue(block)
                                .branchFalse(retBlock)
                                .build()
                )
        );
        Assert.assertEquals(
                retBlock.getInstructions(),
                Arrays.asList(
                        Return.createVoid()
                )
        );
    }

    @Test
    public void strayMoveExceptionFailsToVerify() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, 5),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);

        Assert.assertThrows(DexVerifyException.class, parser::run);
    }

    @Test
    public void sparseSwitch() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                new ImmutableInstruction31t(Opcode.SPARSE_SWITCH, 0, 10),
                new ImmutableInstruction31i(Opcode.CONST, 0, 2),
                new ImmutableInstruction31i(Opcode.CONST, 0, 3),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutableSparseSwitchPayload(ImmutableList.of(new ImmutableSwitchElement(123, 6),
                                                                  new ImmutableSwitchElement(456, 9)))
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);
        BasicBlock entry = parser.run();
        Switch sw = (Switch) entry.getTerminatingInstruction();
        BasicBlock block1 = sw.getDefaultBranch();
        BasicBlock block2 = sw.getBranches().get(123);
        BasicBlock block3 = sw.getBranches().get(456);
        Switch expectedSwitch = Switch.create(r(0, LocalVariable.Type.NARROW), block1);
        expectedSwitch.addBranch(123, block2);
        expectedSwitch.addBranch(456, block3);
        Assert.assertEquals(
                entry.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                        expectedSwitch
                )
        );
        Assert.assertEquals(
                block1.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 2),
                        GoTo.create(block2)
                )
        );
        Assert.assertEquals(
                block2.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 3),
                        GoTo.create(block3)
                )
        );
        Assert.assertEquals(
                block3.getInstructions(),
                Arrays.asList(
                        Return.createVoid()
                )
        );
    }

    @Test
    public void packedSwitch() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 1),
                new ImmutableInstruction31t(Opcode.PACKED_SWITCH, 0, 10),
                new ImmutableInstruction31i(Opcode.CONST, 0, 2),
                new ImmutableInstruction31i(Opcode.CONST, 0, 3),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutablePackedSwitchPayload(ImmutableList.of(new ImmutableSwitchElement(5, 6),
                                                                  new ImmutableSwitchElement(6, 9)))
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);
        BasicBlock entry = parser.run();
        Switch sw = (Switch) entry.getTerminatingInstruction();
        BasicBlock block1 = sw.getDefaultBranch();
        BasicBlock block2 = sw.getBranches().get(5);
        BasicBlock block3 = sw.getBranches().get(6);
        Switch expectedSwitch = Switch.create(r(0, LocalVariable.Type.NARROW), block1);
        expectedSwitch.addBranch(5, block2);
        expectedSwitch.addBranch(6, block3);
        Assert.assertEquals(
                entry.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 1),
                        expectedSwitch
                )
        );
        Assert.assertEquals(
                block1.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 2),
                        GoTo.create(block2)
                )
        );
        Assert.assertEquals(
                block2.getInstructions(),
                Arrays.asList(
                        Const.createNarrow(r(0, LocalVariable.Type.NARROW), 3),
                        GoTo.create(block3)
                )
        );
        Assert.assertEquals(
                block3.getInstructions(),
                Arrays.asList(
                        Return.createVoid()
                )
        );
    }

    @Test
    public void zeroBranchNormal() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                new ImmutableInstruction31i(Opcode.CONST, 1, 1),
                new ImmutableInstruction22t(Opcode.IF_EQ, 0, 1, 3),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);
        BasicBlock entry = parser.run();
        BasicBlock near = ((Branch) entry.getTerminatingInstruction()).getBranchFalse();
        BasicBlock far = ((Branch) entry.getTerminatingInstruction()).getBranchTrue();
        Assert.assertNotSame(near, far);
        Assert.assertEquals(entry.getInstructions(), Arrays.asList(
                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                Const.createNarrow(r(1, LocalVariable.Type.NARROW), 1),
                Branch.builder()
                        .type(Branch.Type.EQUAL)
                        .lhs(r(0, LocalVariable.Type.NARROW))
                        .rhs(r(1, LocalVariable.Type.NARROW))
                        .branchTrue(far)
                        .branchFalse(near)
                        .build()
        ));
    }

    @Test
    public void zeroBranchEliminated() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                new ImmutableInstruction31i(Opcode.CONST, 1, 0),
                new ImmutableInstruction22t(Opcode.IF_EQ, 0, 1, 3),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);
        BasicBlock entry = parser.run();
        BasicBlock far = ((GoTo) entry.getTerminatingInstruction()).getTarget();
        Assert.assertEquals(entry.getInstructions(), Arrays.asList(
                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                Const.createNarrow(r(1, LocalVariable.Type.NARROW), 0),
                Const.create(r(1, LocalVariable.Type.REFERENCE), Const.NULL),
                GoTo.create(far)
        ));
    }

    @Test
    public void zeroBranchCmpzEliminated() {
        InstructionList list = new InstructionList(Arrays.asList(
                new ImmutableInstruction31i(Opcode.CONST, 0, 0),
                new ImmutableInstruction21t(Opcode.IF_EQZ, 0, 3),
                new ImmutableInstruction10x(Opcode.RETURN_VOID),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);
        BasicBlock entry = parser.run();
        BasicBlock far = ((GoTo) entry.getTerminatingInstruction()).getTarget();
        Assert.assertEquals(entry.getInstructions(), Arrays.asList(
                Const.createNarrow(r(0, LocalVariable.Type.NARROW), 0),
                Const.create(r(0, LocalVariable.Type.REFERENCE), Const.NULL),
                GoTo.create(far)
        ));
    }

    @Test
    public void delayedMoveResult() {
        InstructionList list = new InstructionList(ImmutableList.of(
                new ImmutableInstruction35c(Opcode.FILLED_NEW_ARRAY, 0, 0, 0, 0, 0, 0,
                                            new ImmutableTypeReference("[I")),
                new ImmutableInstruction10t(Opcode.GOTO, 1),
                new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));
        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();
        InstructionParser parser = new InstructionParser(classpath, list, typeChecker);
        BasicBlock entry = parser.run();
        BasicBlock far = ((GoTo) entry.getTerminatingInstruction()).getTarget();
        Assert.assertEquals(entry.getInstructions(), Arrays.asList(
                NewArray.variableBuilder()
                        .target(r(1, LocalVariable.Type.REFERENCE))
                        .type((ArrayTypeMirror) type("[I"))
                        .build(),
                GoTo.create(far)
        ), entry.getInstructions().toString());
        Assert.assertEquals(far.getInstructions(), Arrays.asList(
                Return.createVoid()
        ));
    }

    @Test
    public void suppressLinkageErrors() {
        InstructionList list = new InstructionList(ImmutableList.of(
                new ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                        new ImmutableMethodReference("LEmptyClass;", "abc", ImmutableList.of(), "V")),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)
        ));

        classpath.createClass(Type.getType("LEmptyClass;"));

        TypeChecker typeChecker = new TypeChecker(list);
        typeChecker.run();

        InstructionParser parser1 = new InstructionParser(classpath, list, typeChecker);
        parser1.errorHandler = DexParserErrorHandler.getLenient();
        parser1.run();

        Assert.assertThrows(NoSuchMemberException.class, () -> {
            InstructionParser parser2 = new InstructionParser(classpath, list, typeChecker);
            parser2.errorHandler = DexParserErrorHandler.getDefault();
            parser2.run();
        });
    }
}