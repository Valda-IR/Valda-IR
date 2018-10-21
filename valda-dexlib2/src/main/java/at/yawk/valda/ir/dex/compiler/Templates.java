package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.code.ArrayLength;
import at.yawk.valda.ir.code.ArrayLoadStore;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.collections.api.ByteIterable;
import org.eclipse.collections.api.CharIterable;
import org.eclipse.collections.api.DoubleIterable;
import org.eclipse.collections.api.FloatIterable;
import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.LongIterable;
import org.eclipse.collections.api.PrimitiveIterable;
import org.eclipse.collections.api.ShortIterable;
import org.jf.dexlib2.Format;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.SwitchPayload;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.immutable.instruction.ImmutableArrayPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction12x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21ih;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21lh;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22b;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22s;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction23x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31i;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction32x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction51l;
import org.jf.dexlib2.immutable.instruction.ImmutablePackedSwitchPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableSparseSwitchPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableSwitchElement;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@SuppressWarnings("FieldNamingConvention")
final class Templates {
    private static final LocalVariable TEMP_REF = LocalVariable.reference(Templates.class.getName() + ".TEMP_REF");

    private static final InstructionTemplate<Move> MOVE = new InstructionTemplate<>(Move.class)
            .outLocal(4, Move::getTo)
            .inLocal(4, Move::getFrom)
            .compile(Format.Format12x, (ctx, move) -> {
                LocalVariable.Type type = move.getFrom().getType();
                if (type != move.getTo().getType()) {
                    throw new InstructionCompileException(move, "Move type mismatch");
                }
                Opcode op;
                if (type == LocalVariable.Type.WIDE) {
                    op = Opcode.MOVE_WIDE;
                } else if (type == LocalVariable.Type.NARROW) {
                    op = Opcode.MOVE;
                } else {
                    op = Opcode.MOVE_OBJECT;
                }
                return new ImmutableInstruction12x(op,
                                                   ctx.outReg(move.getTo()),
                                                   ctx.inReg(move.getFrom()));
            });
    private static final InstructionTemplate<Move> MOVE_FROM16 = new InstructionTemplate<>(Move.class)
            .outLocal(4, Move::getTo)
            .inLocal(16, Move::getFrom)
            .compile(Format.Format22x, (ctx, move) -> {
                LocalVariable.Type type = move.getFrom().getType();
                if (type != move.getTo().getType()) {
                    throw new InstructionCompileException(move, "Move type mismatch");
                }
                Opcode op;
                if (type == LocalVariable.Type.WIDE) {
                    op = Opcode.MOVE_WIDE_FROM16;
                } else if (type == LocalVariable.Type.NARROW) {
                    op = Opcode.MOVE_FROM16;
                } else {
                    op = Opcode.MOVE_OBJECT_FROM16;
                }
                return new ImmutableInstruction22x(op,
                                                   ctx.outReg(move.getTo()),
                                                   ctx.inReg(move.getFrom()));
            });
    private static final InstructionTemplate<Move> MOVE_16 = new InstructionTemplate<>(Move.class)
            .outLocal(16, Move::getTo)
            .inLocal(16, Move::getFrom)
            .compile(Format.Format32x, (ctx, move) -> {
                LocalVariable.Type type = move.getFrom().getType();
                if (type != move.getTo().getType()) {
                    throw new InstructionCompileException(move, "Move type mismatch");
                }
                Opcode op;
                if (type == LocalVariable.Type.WIDE) {
                    op = Opcode.MOVE_WIDE_16;
                } else if (type == LocalVariable.Type.NARROW) {
                    op = Opcode.MOVE_16;
                } else {
                    op = Opcode.MOVE_OBJECT_16;
                }
                return new ImmutableInstruction32x(op,
                                                   ctx.outReg(move.getTo()),
                                                   ctx.inReg(move.getFrom()));
            });

    /**
     * Move that is just "local variable renaming" and produces no code
     */
    private static final InstructionTemplate<Move> SMART_MOVE = new InstructionTemplate<>(Move.class)
            .sameRegister(Move::getFrom, Move::getTo)
            .compile(new Format[0], (ctx, move) -> new Instruction[0]);
    private static final InstructionTemplate<Return> RETURN_VOID = new InstructionTemplate<>(Return.class)
            .precondition(r -> r.getReturnValue() == null)
            .compile(Format.Format10x, (ctx, insn) -> new ImmutableInstruction10x(Opcode.RETURN_VOID));
    private static final InstructionTemplate<Return> RETURN = new InstructionTemplate<>(Return.class)
            .precondition(r -> r.getReturnValue() != null)
            .inLocal(8, Return::getReturnValue)
            .compile(Format.Format11x, (ctx, insn) -> {
                LocalVariable r = insn.getReturnValue();
                assert r != null;
                return new ImmutableInstruction11x(
                        r.getType() == LocalVariable.Type.NARROW ? Opcode.RETURN :
                                r.getType() == LocalVariable.Type.WIDE ? Opcode.RETURN_WIDE : Opcode.RETURN_OBJECT,
                        ctx.inReg(r)
                );
            });
    private static final InstructionTemplate<Const> CONST = new InstructionTemplate<>(Const.class)
            .precondition(c -> c.getValue() instanceof Const.Narrow)
            .outLocal(8, Const::getTarget)
            .compile(Format.Format31i, (ctx, insn) ->
                    new ImmutableInstruction31i(Opcode.CONST,
                                                ctx.outReg(insn.getTarget()),
                                                ((Const.Narrow) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_4 = new InstructionTemplate<>(Const.class)
            .precondition(c -> {
                if (!(c.getValue() instanceof Const.Narrow)) { return false; }
                int value = ((Const.Narrow) c.getValue()).getValue();
                return value >= -8 && value <= 7;
            })
            .outLocal(4, Const::getTarget)
            .compile(Format.Format11n, (ctx, insn) ->
                    new ImmutableInstruction11n(Opcode.CONST_4,
                                                ctx.outReg(insn.getTarget()),
                                                ((Const.Narrow) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_4_NULL = new InstructionTemplate<>(Const.class)
            .precondition(c -> c.getValue() instanceof Const.Null)
            .outLocal(4, Const::getTarget)
            .compile(Format.Format11n, (ctx, insn) ->
                    new ImmutableInstruction11n(Opcode.CONST_4, ctx.outReg(insn.getTarget()), 0));
    private static final InstructionTemplate<Const> CONST_16 = new InstructionTemplate<>(Const.class)
            .precondition(c -> {
                if (!(c.getValue() instanceof Const.Narrow)) { return false; }
                int value = ((Const.Narrow) c.getValue()).getValue();
                return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
            })
            .outLocal(8, Const::getTarget)
            .compile(Format.Format21s, (ctx, insn) ->
                    new ImmutableInstruction21s(Opcode.CONST_16,
                                                ctx.outReg(insn.getTarget()),
                                                ((Const.Narrow) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_16_NULL = new InstructionTemplate<>(Const.class)
            .precondition(c -> c.getValue() instanceof Const.Null)
            .outLocal(8, Const::getTarget)
            .compile(Format.Format21s, (ctx, insn) ->
                    new ImmutableInstruction21s(Opcode.CONST_16, ctx.outReg(insn.getTarget()), 0));
    private static final InstructionTemplate<Const> CONST_HIGH16 = new InstructionTemplate<>(Const.class)
            .precondition(c -> {
                if (!(c.getValue() instanceof Const.Narrow)) { return false; }
                int value = ((Const.Narrow) c.getValue()).getValue();
                return (value & 0xffff) == 0;
            })
            .outLocal(8, Const::getTarget)
            .compile(Format.Format21ih, (ctx, insn) ->
                    new ImmutableInstruction21ih(Opcode.CONST_HIGH16,
                                                 ctx.outReg(insn.getTarget()),
                                                 ((Const.Narrow) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_WIDE_16 = new InstructionTemplate<>(Const.class)
            .precondition(c -> {
                if (!(c.getValue() instanceof Const.Wide)) { return false; }
                long value = ((Const.Wide) c.getValue()).getValue();
                return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
            })
            .outLocal(8, Const::getTarget)
            .compile(Format.Format21s, (ctx, insn) ->
                    new ImmutableInstruction21s(Opcode.CONST_WIDE_16,
                                                ctx.outReg(insn.getTarget()),
                                                (int) ((Const.Wide) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_WIDE_32 = new InstructionTemplate<>(Const.class)
            .precondition(c -> {
                if (!(c.getValue() instanceof Const.Wide)) { return false; }
                long value = ((Const.Wide) c.getValue()).getValue();
                return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
            })
            .outLocal(8, Const::getTarget)
            .compile(Format.Format31i, (ctx, insn) ->
                    new ImmutableInstruction31i(Opcode.CONST_WIDE_32,
                                                ctx.outReg(insn.getTarget()),
                                                (int) ((Const.Wide) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_WIDE = new InstructionTemplate<>(Const.class)
            .precondition(c -> c.getValue() instanceof Const.Wide)
            .outLocal(8, Const::getTarget)
            .compile(Format.Format51l, (ctx, insn) ->
                    new ImmutableInstruction51l(Opcode.CONST_WIDE,
                                                ctx.outReg(insn.getTarget()),
                                                ((Const.Wide) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_WIDE_HIGH16 = new InstructionTemplate<>(Const.class)
            .precondition(c -> {
                if (!(c.getValue() instanceof Const.Wide)) { return false; }
                long value = ((Const.Wide) c.getValue()).getValue();
                return (value & 0xffffffffffffL) == 0;
            })
            .outLocal(8, Const::getTarget)
            .compile(Format.Format21lh, (ctx, insn) ->
                    new ImmutableInstruction21lh(Opcode.CONST_WIDE_HIGH16,
                                                 ctx.outReg(insn.getTarget()),
                                                 ((Const.Wide) insn.getValue()).getValue()));
    private static final InstructionTemplate<Const> CONST_STRING = new InstructionTemplate<>(Const.class)
            .precondition(c -> c.getValue() instanceof Const.String)
            .outLocal(8, Const::getTarget)
            .string(16, c -> ((Const.String) c.getValue()).getValue())
            .compile(Format.Format21c, (ctx, insn) ->
                    new ImmutableInstruction21c(Opcode.CONST_STRING,
                                                ctx.outReg(insn.getTarget()),
                                                ctx.string(((Const.String) insn.getValue()).getValue())));
    private static final InstructionTemplate<Const> CONST_STRING_JUMBO = new InstructionTemplate<>(Const.class)
            .precondition(c -> c.getValue() instanceof Const.String)
            .outLocal(8, Const::getTarget)
            .compile(Format.Format31c, (ctx, insn) ->
                    new ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO,
                                                ctx.outReg(insn.getTarget()),
                                                ctx.string(((Const.String) insn.getValue()).getValue())));
    private static final InstructionTemplate<Const> CONST_CLASS = new InstructionTemplate<>(Const.class)
            .precondition(c -> c.getValue() instanceof Const.Class)
            .outLocal(8, Const::getTarget)
            .compile(Format.Format21c, (ctx, insn) ->
                    new ImmutableInstruction21c(Opcode.CONST_CLASS,
                                                ctx.outReg(insn.getTarget()),
                                                ctx.type(((Const.Class) insn.getValue()).getValue())));
    private static final InstructionTemplate<Monitor> MONITOR = new InstructionTemplate<>(Monitor.class)
            .inLocal(8, Monitor::getMonitor)
            .compile(Format.Format11x, (ctx, insn) ->
                    new ImmutableInstruction11x(
                            insn.getType() == Monitor.Type.ENTER ? Opcode.MONITOR_ENTER : Opcode.MONITOR_EXIT,
                            ctx.inReg(insn.getMonitor())));
    private static final InstructionTemplate<CheckCast> CHECK_CAST = new InstructionTemplate<>(CheckCast.class)
            .inLocal(8, CheckCast::getVariable)
            .compile(Format.Format21c, (ctx, insn) ->
                    new ImmutableInstruction21c(Opcode.CHECK_CAST,
                                                ctx.inReg(insn.getVariable()),
                                                ctx.type(insn.getType())));
    private static final InstructionTemplate<InstanceOf> INSTANCE_OF = new InstructionTemplate<>(InstanceOf.class)
            .inLocal(4, InstanceOf::getOperand)
            .outLocal(4, InstanceOf::getTarget)
            .compile(Format.Format22c, (ctx, insn) ->
                    new ImmutableInstruction22c(Opcode.INSTANCE_OF,
                                                ctx.outReg(insn.getTarget()),
                                                ctx.inReg(insn.getOperand()),
                                                ctx.type(insn.getType())));
    private static final InstructionTemplate<ArrayLength> ARRAY_LENGTH = new InstructionTemplate<>(ArrayLength.class)
            .outLocal(4, ArrayLength::getTarget)
            .inLocal(4, ArrayLength::getOperand)
            .compile(Format.Format12x, (ctx, insn) ->
                    new ImmutableInstruction12x(Opcode.ARRAY_LENGTH,
                                                ctx.outReg(insn.getTarget()),
                                                ctx.inReg(insn.getOperand())));

    private static InstructionTemplate<Invoke> invoke(boolean newInstance, boolean range, boolean hasReturn) {
        InstructionTemplate<Invoke> template = new InstructionTemplate<>(Invoke.class);
        template.precondition(i -> {
            if ((i.getType() == Invoke.Type.NEW_INSTANCE) != newInstance) {
                return false;
            }
            if (!range && countParameters(i.getParameters()) > (newInstance ? 4 : 5)) {
                return false;
            }
            //noinspection RedundantIfStatement
            if ((i.getReturnValue() == null) == hasReturn) {
                return false;
            }
            return true;
        });
        template.inLocals(range ? 16 : 4, Invoke::getParameters);
        if (range) {
            template.contiguous(newInstance ? (invoke -> {
                                    // 2018-06-12 see below
                                    // return !hasReturn ? TEMP_REF : invoke.getReturnValue();
                                    return TEMP_REF;
                                }) : null,
                                Invoke::getParameters);
        }
        if (newInstance) {
            if (hasReturn) {
                // 2018-06-12
                // we cannot use outLocalNoOverlap here because the <init> might throw, leaving the out local
                // uninitialized but potentially overwriting its previous value. This goes against the idea that an
                // Invoke instruction is "atomic" (i.e. either completes successfully or leaves its output registers
                // intact)

                // TODO: still support a single local somehow

                // template.outLocalNoOverlap(4, Invoke::getReturnValue);
                template.outLocal(4, Invoke::getReturnValue);
                template.tmpLocal(4, TEMP_REF);
            } else {
                template.tmpLocal(4, TEMP_REF);
            }
        } else if (hasReturn) {
            template.outLocal(4, Invoke::getReturnValue);
        }

        List<Format> formats = new ArrayList<>();
        if (newInstance) { formats.add(Format.Format21c); }
        formats.add(range ? Format.Format3rc : Format.Format35c);
        if (hasReturn) {
            if (newInstance) {
                // see above 2018-06-12, add a move TMP->out
                formats.add(Format.Format12x);
            } else {
                formats.add(Format.Format11x);
            }
        }

        template.compile(formats.toArray(new Format[0]), (ctx, insn) -> {
            Opcode opcode;
            switch (insn.getType()) {
                case NORMAL: {
                    if (insn.getMethod().isStatic()) {
                        opcode = range ? Opcode.INVOKE_STATIC_RANGE : Opcode.INVOKE_STATIC;
                    } else if (insn.getMethod().getDeclaringType().isInterface()) {
                        opcode = range ? Opcode.INVOKE_INTERFACE_RANGE : Opcode.INVOKE_INTERFACE;
                    } else {
                        opcode = range ? Opcode.INVOKE_VIRTUAL_RANGE : Opcode.INVOKE_VIRTUAL;
                    }
                    break;
                }
                case SPECIAL: {
                    if (insn.getMethod().isStatic()) {
                        opcode = range ? Opcode.INVOKE_STATIC_RANGE : Opcode.INVOKE_STATIC;
                    } else if (insn.getMethod().isPrivate() || insn.getMethod().isConstructor()) {
                        opcode = range ? Opcode.INVOKE_DIRECT_RANGE : Opcode.INVOKE_DIRECT;
                    } else {
                        opcode = range ? Opcode.INVOKE_SUPER_RANGE : Opcode.INVOKE_SUPER;
                    }
                    break;
                }
                case NEW_INSTANCE: {
                    opcode = range ? Opcode.INVOKE_DIRECT_RANGE : Opcode.INVOKE_DIRECT;
                    break;
                }
                default:
                    throw new AssertionError();
            }
            Instruction[] instructions =
                    new Instruction[formats.size()];
            int i = 0;
            if (newInstance) {
                instructions[i++] = new ImmutableInstruction21c(
                        Opcode.NEW_INSTANCE,
                        // see above 2018-06-12
                        //!hasReturn ? ctx.tmpReg(TEMP_REF) : ctx.outRegNoOverlap(insn.getReturnValue()),
                        ctx.tmpReg(TEMP_REF),
                        ctx.type(insn.getMethod().getDeclaringType())
                );
            }
            if (range) {
                int startRegister;
                if (newInstance) {
                    // see above 2018-06-12
                    /*if (!hasReturn) {
                        startRegister = ctx.tmpReg(TEMP_REF);
                    } else {
                        startRegister = ctx.outRegNoOverlap(insn.getReturnValue());
                    }*/
                    startRegister = ctx.tmpReg(TEMP_REF);
                } else {
                    if (insn.getParameters().isEmpty()) {
                        startRegister = 0;
                    } else {
                        startRegister = ctx.inReg(insn.getParameters().get(0));
                    }
                }
                int registerCount = countParameters(insn.getParameters());
                if (newInstance) {
                    registerCount++;
                }
                instructions[i++] = new ImmutableInstruction3rc(
                        opcode,
                        startRegister,
                        registerCount,
                        ctx.method(insn.getMethod())
                );
            } else {
                int first = -1;
                if (newInstance) {
                    // see above 2018-06-12
                    /*first = !hasReturn ?
                            ctx.tmpReg(TEMP_REF) :
                            ctx.outRegNoOverlap(insn.getReturnValue());*/
                    first = ctx.tmpReg(TEMP_REF);
                }
                instructions[i++] = fiveRegistersInsn(ctx, first, opcode,
                                                      ctx.type(insn.getMethod().getDeclaringType()),
                                                      insn.getParameters());
            }
            if (hasReturn) {
                if (newInstance) {
                    instructions[i++] = new ImmutableInstruction12x(
                            Opcode.MOVE_OBJECT, ctx.outReg(insn.getReturnValue()), ctx.tmpReg(TEMP_REF));
                } else {
                    assert insn.getReturnValue() != null;
                    Opcode ret;
                    switch (insn.getReturnValue().getType()) {
                        case NARROW: {
                            ret = Opcode.MOVE_RESULT;
                            break;
                        }
                        case WIDE: {
                            ret = Opcode.MOVE_RESULT_WIDE;
                            break;
                        }
                        case REFERENCE: {
                            ret = Opcode.MOVE_RESULT_OBJECT;
                            break;
                        }
                        default:
                            throw new AssertionError();
                    }
                    instructions[i++] = new ImmutableInstruction11x(ret, ctx.outReg(insn.getReturnValue()));
                }
            }
            assert i == instructions.length;
            return instructions;
        });
        return template;
    }

    private static ImmutableInstruction35c fiveRegistersInsn(
            InstructionContext ctx,
            int first,
            Opcode opcode,
            Reference ref,
            List<LocalVariable> variables
    ) {
        int[] regs = new int[5];
        int n = 0;
        if (first != -1) {
            regs[n++] = first;
        }
        for (LocalVariable parameter : variables) {
            regs[n++] = ctx.inReg(parameter);
            if (parameter.getType() == LocalVariable.Type.WIDE) {
                regs[n++] = regs[n - 1] + 1;
            }
        }
        return new ImmutableInstruction35c(
                opcode,
                n,
                regs[0], regs[1], regs[2], regs[3], regs[4],
                ref
        );
    }

    private static int countParameters(List<LocalVariable> parameters) {
        int c = 0;
        for (LocalVariable parameter : parameters) {
            if (parameter.getType() == LocalVariable.Type.WIDE) {
                c += 2;
            } else {
                c++;
            }
        }
        return c;
    }

    private static final InstructionTemplate<NewArray> NEW_ARRAY_LENGTH = new InstructionTemplate<>(NewArray.class)
            .precondition(na -> !na.hasVariables())
            .inLocal(4, NewArray::getLength)
            .outLocal(4, NewArray::getTarget)
            .compile(Format.Format22c, (ctx, insn) -> new ImmutableInstruction22c(
                    Opcode.NEW_ARRAY,
                    ctx.outReg(insn.getTarget()),
                    ctx.inReg(insn.getLength()),
                    ctx.type(insn.getType())
            ));
    private static final InstructionTemplate<NewArray> NEW_ARRAY_FILLED_NORMAL =
            new InstructionTemplate<>(NewArray.class)
                    .precondition(na -> na.hasVariables() && countParameters(na.getVariables()) <= 5)
                    .inLocals(4, NewArray::getVariables)
                    .outLocal(4, NewArray::getTarget)
                    .compile(new Format[]{ Format.Format35c, Format.Format11x }, (ctx, insn) ->
                            new Instruction[]{
                                    fiveRegistersInsn(
                                            ctx, -1,
                                            Opcode.FILLED_NEW_ARRAY,
                                            ctx.type(insn.getType()),
                                            insn.getVariables()
                                    ),
                                    new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, ctx.outReg(insn.getTarget()))
                            });
    private static final InstructionTemplate<NewArray> NEW_ARRAY_FILLED_RANGE =
            new InstructionTemplate<>(NewArray.class)
                    .precondition(NewArray::hasVariables)
                    .contiguous(null, NewArray::getVariables)
                    .outLocal(4, NewArray::getTarget)
                    .compile(new Format[]{ Format.Format3rc, Format.Format11x }, (ctx, insn) ->
                            new Instruction[]{
                                    new ImmutableInstruction3rc(
                                            Opcode.FILLED_NEW_ARRAY_RANGE,
                                            insn.getVariables().isEmpty() ? 0 : ctx.inReg(insn.getVariables().get(0)),
                                            countParameters(insn.getVariables()),
                                            ctx.type(insn.getType())
                                    ),
                                    new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, ctx.outReg(insn.getTarget()))
                            });
    private static final InstructionTemplate<FillArray> FILL_ARRAY_DATA = new InstructionTemplate<>(FillArray.class)
            .inLocal(8, FillArray::getArray)
            .compile(Format.Format31t, (ctx, insn) -> {
                PrimitiveIterable contents = insn.getContents();
                ImmutableList.Builder<Number> builder = ImmutableList.builderWithExpectedSize(contents.size());
                int width;
                if (contents instanceof ByteIterable) {
                    ((ByteIterable) contents).forEach(builder::add);
                    width = 1;
                } else if (contents instanceof ShortIterable) {
                    ((ShortIterable) contents).forEach(builder::add);
                    width = 2;
                } else if (contents instanceof CharIterable) {
                    ((CharIterable) contents).forEach(c -> builder.add((short) c));
                    width = 2;
                } else if (contents instanceof IntIterable) {
                    ((IntIterable) contents).forEach(builder::add);
                    width = 4;
                } else if (contents instanceof FloatIterable) {
                    ((FloatIterable) contents).forEach(f -> builder.add(Float.floatToIntBits(f)));
                    width = 4;
                } else if (contents instanceof LongIterable) {
                    ((LongIterable) contents).forEach(builder::add);
                    width = 8;
                } else if (contents instanceof DoubleIterable) {
                    ((DoubleIterable) contents).forEach(d -> builder.add(Double.doubleToLongBits(d)));
                    width = 8;
                } else {
                    throw new InstructionCompileException(insn, "Unsupported content type");
                }
                return new ImmutableInstruction31t(
                        Opcode.FILL_ARRAY_DATA,
                        ctx.inReg(insn.getArray()),
                        ctx.offsetToPayload(new ImmutableArrayPayload(width, builder.build()))
                );
            });
    private static final InstructionTemplate<Throw> THROW = new InstructionTemplate<>(Throw.class)
            .inLocal(8, Throw::getException)
            .compile(Format.Format11x, (ctx, insn) -> new ImmutableInstruction11x(
                    Opcode.THROW,
                    ctx.inReg(insn.getException())
            ));
    private static final InstructionTemplate<GoTo> GOTO = new InstructionTemplate<>(GoTo.class)
            // code is managed by continueTo
            .compile(new Format[0], (ctx, insn) -> new Instruction[0])
            .continueTo(GoTo::getTarget);
    private static final InstructionTemplate<Switch> SWITCH = new InstructionTemplate<>(Switch.class)
            .inLocal(8, Switch::getOperand)
            .blocks(32, sw -> sw.getBranches().values())
            .compile(Format.Format31t, (ctx, insn) -> {
                boolean packed = true;
                int[] keys = insn.getBranches().keysView().toSortedArray();
                for (int i = 0; i < keys.length - 1; i++) {
                    if (keys[i] != keys[i + 1] - 1) {
                        packed = false;
                        break;
                    }
                }
                ImmutableList.Builder<ImmutableSwitchElement> builder =
                        ImmutableList.builderWithExpectedSize(keys.length);
                for (int key : keys) {
                    builder.add(new ImmutableSwitchElement(key, ctx.offsetToBlock(insn.getBranches().get(key))));
                }
                SwitchPayload switchPayload =
                        packed ? new ImmutablePackedSwitchPayload(builder.build()) :
                                new ImmutableSparseSwitchPayload(builder.build());
                return new ImmutableInstruction31t(packed ? Opcode.PACKED_SWITCH : Opcode.SPARSE_SWITCH,
                                                   ctx.inReg(insn.getOperand()),
                                                   ctx.offsetToPayload(switchPayload));
            })
            .continueTo(Switch::getDefaultBranch);

    private static final InstructionTemplate<BinaryOperation> BINARY = new InstructionTemplate<>(BinaryOperation.class)
            .inLocal(8, BinaryOperation::getLhs)
            .inLocal(8, BinaryOperation::getRhs)
            .outLocal(8, BinaryOperation::getDestination)
            .compile(Format.Format23x, (ctx, insn) -> {
                if (insn.getDestination().getType() != insn.getType().getOutType()) {
                    throw new InstructionCompileException(
                            insn,
                            "Destination type for " + insn.getType() + " must be " + insn.getType().getOutType() +
                            " but was " + insn.getDestination().getType());
                }
                if (insn.getLhs().getType() != insn.getType().getLhsType()) {
                    throw new InstructionCompileException(
                            insn,
                            "LHS type for " + insn.getType() + " must be " + insn.getType().getOutType() +
                            " but was " + insn.getDestination().getType());
                }
                if (insn.getRhs().getType() != insn.getType().getRhsType()) {
                    throw new InstructionCompileException(
                            insn,
                            "RHS type for " + insn.getType() + " must be " + insn.getType().getOutType() +
                            " but was " + insn.getDestination().getType());
                }
                return new ImmutableInstruction23x(
                        toOpcode(insn.getType()),
                        ctx.outReg(insn.getDestination()),
                        ctx.inReg(insn.getLhs()),
                        ctx.inReg(insn.getRhs())
                );
            });

    private static Opcode toOpcode(BinaryOperation.Type type) {
        switch (type) {
            case COMPARE_FLOAT_BIAS_L:
                return Opcode.CMPL_FLOAT;
            case COMPARE_FLOAT_BIAS_G:
                return Opcode.CMPG_FLOAT;
            case COMPARE_DOUBLE_BIAS_L:
                return Opcode.CMPL_DOUBLE;
            case COMPARE_DOUBLE_BIAS_G:
                return Opcode.CMPG_DOUBLE;
            case COMPARE_LONG:
                return Opcode.CMP_LONG;
            case ADD_INT:
                return Opcode.ADD_INT;
            case SUB_INT:
                return Opcode.SUB_INT;
            case MUL_INT:
                return Opcode.MUL_INT;
            case DIV_INT:
                return Opcode.DIV_INT;
            case REM_INT:
                return Opcode.REM_INT;
            case AND_INT:
                return Opcode.AND_INT;
            case OR_INT:
                return Opcode.OR_INT;
            case XOR_INT:
                return Opcode.XOR_INT;
            case SHL_INT:
                return Opcode.SHL_INT;
            case SHR_INT:
                return Opcode.SHR_INT;
            case USHR_INT:
                return Opcode.USHR_INT;
            case ADD_LONG:
                return Opcode.ADD_LONG;
            case SUB_LONG:
                return Opcode.SUB_LONG;
            case MUL_LONG:
                return Opcode.MUL_LONG;
            case DIV_LONG:
                return Opcode.DIV_LONG;
            case REM_LONG:
                return Opcode.REM_LONG;
            case AND_LONG:
                return Opcode.AND_LONG;
            case OR_LONG:
                return Opcode.OR_LONG;
            case XOR_LONG:
                return Opcode.XOR_LONG;
            case SHL_LONG:
                return Opcode.SHL_LONG;
            case SHR_LONG:
                return Opcode.SHR_LONG;
            case USHR_LONG:
                return Opcode.USHR_LONG;
            case ADD_FLOAT:
                return Opcode.ADD_FLOAT;
            case SUB_FLOAT:
                return Opcode.SUB_FLOAT;
            case MUL_FLOAT:
                return Opcode.MUL_FLOAT;
            case DIV_FLOAT:
                return Opcode.DIV_FLOAT;
            case REM_FLOAT:
                return Opcode.REM_FLOAT;
            case ADD_DOUBLE:
                return Opcode.ADD_DOUBLE;
            case SUB_DOUBLE:
                return Opcode.SUB_DOUBLE;
            case MUL_DOUBLE:
                return Opcode.MUL_DOUBLE;
            case DIV_DOUBLE:
                return Opcode.DIV_DOUBLE;
            case REM_DOUBLE:
                return Opcode.REM_DOUBLE;
            default:
                throw new AssertionError();
        }
    }

    private static final InstructionTemplate<BinaryOperation> BINARY_IN_PLACE =
            new InstructionTemplate<>(BinaryOperation.class)
                    .precondition(bo -> toOpcodeInPlace(bo.getType()) != null)
                    .inLocal(4, BinaryOperation::getLhs)
                    .inLocal(4, BinaryOperation::getRhs)
                    .outLocal(4, BinaryOperation::getDestination)
                    .sameRegister(BinaryOperation::getDestination, BinaryOperation::getLhs)
                    .compile(Format.Format12x, (ctx, insn) -> {
                        Opcode opcode = toOpcodeInPlace(insn.getType());
                        assert opcode != null;
                        return new ImmutableInstruction12x(
                                opcode,
                                ctx.outReg(insn.getDestination()),
                                ctx.inReg(insn.getRhs())
                        );
                    });

    private static Opcode toOpcodeInPlace(BinaryOperation.Type type) {
        switch (type) {
            case COMPARE_FLOAT_BIAS_L:
            case COMPARE_FLOAT_BIAS_G:
            case COMPARE_DOUBLE_BIAS_L:
            case COMPARE_DOUBLE_BIAS_G:
            case COMPARE_LONG:
                return null;
            case ADD_INT:
                return Opcode.ADD_INT_2ADDR;
            case SUB_INT:
                return Opcode.SUB_INT_2ADDR;
            case MUL_INT:
                return Opcode.MUL_INT_2ADDR;
            case DIV_INT:
                return Opcode.DIV_INT_2ADDR;
            case REM_INT:
                return Opcode.REM_INT_2ADDR;
            case AND_INT:
                return Opcode.AND_INT_2ADDR;
            case OR_INT:
                return Opcode.OR_INT_2ADDR;
            case XOR_INT:
                return Opcode.XOR_INT_2ADDR;
            case SHL_INT:
                return Opcode.SHL_INT_2ADDR;
            case SHR_INT:
                return Opcode.SHR_INT_2ADDR;
            case USHR_INT:
                return Opcode.USHR_INT_2ADDR;
            case ADD_LONG:
                return Opcode.ADD_LONG_2ADDR;
            case SUB_LONG:
                return Opcode.SUB_LONG_2ADDR;
            case MUL_LONG:
                return Opcode.MUL_LONG_2ADDR;
            case DIV_LONG:
                return Opcode.DIV_LONG_2ADDR;
            case REM_LONG:
                return Opcode.REM_LONG_2ADDR;
            case AND_LONG:
                return Opcode.AND_LONG_2ADDR;
            case OR_LONG:
                return Opcode.OR_LONG_2ADDR;
            case XOR_LONG:
                return Opcode.XOR_LONG_2ADDR;
            case SHL_LONG:
                return Opcode.SHL_LONG_2ADDR;
            case SHR_LONG:
                return Opcode.SHR_LONG_2ADDR;
            case USHR_LONG:
                return Opcode.USHR_LONG_2ADDR;
            case ADD_FLOAT:
                return Opcode.ADD_FLOAT_2ADDR;
            case SUB_FLOAT:
                return Opcode.SUB_FLOAT_2ADDR;
            case MUL_FLOAT:
                return Opcode.MUL_FLOAT_2ADDR;
            case DIV_FLOAT:
                return Opcode.DIV_FLOAT_2ADDR;
            case REM_FLOAT:
                return Opcode.REM_FLOAT_2ADDR;
            case ADD_DOUBLE:
                return Opcode.ADD_DOUBLE_2ADDR;
            case SUB_DOUBLE:
                return Opcode.SUB_DOUBLE_2ADDR;
            case MUL_DOUBLE:
                return Opcode.MUL_DOUBLE_2ADDR;
            case DIV_DOUBLE:
                return Opcode.DIV_DOUBLE_2ADDR;
            case REM_DOUBLE:
                return Opcode.REM_DOUBLE_2ADDR;
            default:
                throw new AssertionError();
        }
    }

    private static final InstructionTemplate<LiteralBinaryOperation> BINARY_LITERAL_16 =
            new InstructionTemplate<>(LiteralBinaryOperation.class)
                    .precondition(op -> literalBinaryToOpcode(op.getType(), true) != null)
                    .outLocal(4, LiteralBinaryOperation::getDestination)
                    .inLocal(4, LiteralBinaryOperation::getLhs)
                    .compile(Format.Format22s, (ctx, insn) -> {
                        Opcode opcode = literalBinaryToOpcode(insn.getType(), true);
                        assert opcode != null;
                        return new ImmutableInstruction22s(
                                opcode,
                                ctx.outReg(insn.getDestination()),
                                ctx.inReg(insn.getLhs()),
                                insn.getRhs()
                        );
                    });

    private static final InstructionTemplate<LiteralBinaryOperation> BINARY_LITERAL_8 =
            new InstructionTemplate<>(LiteralBinaryOperation.class)
                    .precondition(op -> op.getRhs() >= Byte.MIN_VALUE && op.getRhs() <= Byte.MAX_VALUE)
                    .outLocal(8, LiteralBinaryOperation::getDestination)
                    .inLocal(8, LiteralBinaryOperation::getLhs)
                    .compile(Format.Format22b, (ctx, insn) -> {
                        Opcode opcode = literalBinaryToOpcode(insn.getType(), false);
                        assert opcode != null;
                        return new ImmutableInstruction22b(
                                opcode,
                                ctx.outReg(insn.getDestination()),
                                ctx.inReg(insn.getLhs()),
                                insn.getRhs()
                        );
                    });

    private static Opcode literalBinaryToOpcode(LiteralBinaryOperation.Type type, boolean wide) {
        switch (type) {
            case ADD:
                return wide ? Opcode.ADD_INT_LIT16 : Opcode.ADD_INT_LIT8;
            case RSUB:
                return wide ? Opcode.RSUB_INT : Opcode.RSUB_INT_LIT8;
            case MUL:
                return wide ? Opcode.MUL_INT_LIT16 : Opcode.MUL_INT_LIT8;
            case DIV:
                return wide ? Opcode.DIV_INT_LIT16 : Opcode.DIV_INT_LIT8;
            case REM:
                return wide ? Opcode.REM_INT_LIT16 : Opcode.REM_INT_LIT8;
            case AND:
                return wide ? Opcode.AND_INT_LIT16 : Opcode.AND_INT_LIT8;
            case OR:
                return wide ? Opcode.OR_INT_LIT16 : Opcode.OR_INT_LIT8;
            case XOR:
                return wide ? Opcode.XOR_INT_LIT16 : Opcode.XOR_INT_LIT8;
            case SHL:
                return wide ? null : Opcode.SHL_INT_LIT8;
            case SHR:
                return wide ? null : Opcode.SHR_INT_LIT8;
            case USHR:
                return wide ? null : Opcode.USHR_INT_LIT8;
            default:
                throw new AssertionError();
        }
    }

    private static final InstructionTemplate<UnaryOperation> UNARY_OPERATION =
            new InstructionTemplate<>(UnaryOperation.class)
                    .outLocal(4, UnaryOperation::getDestination)
                    .inLocal(4, UnaryOperation::getSource)
                    .compile(Format.Format12x, (ctx, insn) -> new ImmutableInstruction12x(
                            unaryToOpcode(insn.getType()),
                            ctx.outReg(insn.getDestination()),
                            ctx.inReg(insn.getSource())
                    ));

    private static Opcode unaryToOpcode(UnaryOperation.Type type) {
        switch (type) {
            case NEGATE_INT:
                return Opcode.NEG_INT;
            case NEGATE_LONG:
                return Opcode.NEG_LONG;
            case NEGATE_FLOAT:
                return Opcode.NEG_FLOAT;
            case NEGATE_DOUBLE:
                return Opcode.NEG_DOUBLE;
            case NOT_INT:
                return Opcode.NOT_INT;
            case NOT_LONG:
                return Opcode.NOT_LONG;
            case INT_TO_LONG:
                return Opcode.INT_TO_LONG;
            case INT_TO_FLOAT:
                return Opcode.INT_TO_FLOAT;
            case INT_TO_DOUBLE:
                return Opcode.INT_TO_DOUBLE;
            case LONG_TO_INT:
                return Opcode.LONG_TO_INT;
            case LONG_TO_FLOAT:
                return Opcode.LONG_TO_FLOAT;
            case LONG_TO_DOUBLE:
                return Opcode.LONG_TO_DOUBLE;
            case FLOAT_TO_INT:
                return Opcode.FLOAT_TO_INT;
            case FLOAT_TO_LONG:
                return Opcode.FLOAT_TO_LONG;
            case FLOAT_TO_DOUBLE:
                return Opcode.FLOAT_TO_DOUBLE;
            case DOUBLE_TO_INT:
                return Opcode.DOUBLE_TO_INT;
            case DOUBLE_TO_LONG:
                return Opcode.DOUBLE_TO_LONG;
            case DOUBLE_TO_FLOAT:
                return Opcode.DOUBLE_TO_FLOAT;
            case INT_TO_BYTE:
                return Opcode.INT_TO_BYTE;
            case INT_TO_CHAR:
                return Opcode.INT_TO_CHAR;
            case INT_TO_SHORT:
                return Opcode.INT_TO_SHORT;
            default:
                throw new AssertionError();
        }
    }

    private static final InstructionTemplate<Branch> IF_NORMAL =
            new InstructionTemplate<>(Branch.class)
                    .precondition(b -> b.getRhs() != null)
                    .inLocal(4, Branch::getLhs)
                    .inLocal(4, Branch::getRhs)
                    .block(16, Branch::getBranchTrue)
                    .compile(Format.Format22t, (ctx, insn) -> new ImmutableInstruction22t(
                            branchToOpcode(insn.getType(), false, false),
                            ctx.inReg(insn.getLhs()),
                            ctx.inReg(insn.getRhs()),
                            ctx.offsetToBlock(insn.getBranchTrue())
                    ))
                    .continueTo(Branch::getBranchFalse);
    private static final InstructionTemplate<Branch> IF_INVERTED =
            new InstructionTemplate<>(Branch.class)
                    .precondition(b -> b.getRhs() != null)
                    .inLocal(4, Branch::getLhs)
                    .inLocal(4, Branch::getRhs)
                    .block(16, Branch::getBranchFalse)
                    .compile(Format.Format22t, (ctx, insn) -> new ImmutableInstruction22t(
                            branchToOpcode(insn.getType(), true, false),
                            ctx.inReg(insn.getLhs()),
                            ctx.inReg(insn.getRhs()),
                            ctx.offsetToBlock(insn.getBranchFalse())
                    ))
                    .continueTo(Branch::getBranchTrue);

    private static final InstructionTemplate<Branch> IF_ZERO_NORMAL =
            new InstructionTemplate<>(Branch.class)
                    .precondition(b -> b.getRhs() == null)
                    .inLocal(8, Branch::getLhs)
                    .block(16, Branch::getBranchTrue)
                    .compile(Format.Format21t, (ctx, insn) -> new ImmutableInstruction21t(
                            branchToOpcode(insn.getType(), false, true),
                            ctx.inReg(insn.getLhs()),
                            ctx.offsetToBlock(insn.getBranchTrue())
                    ))
                    .continueTo(Branch::getBranchFalse);
    private static final InstructionTemplate<Branch> IF_ZERO_INVERTED =
            new InstructionTemplate<>(Branch.class)
                    .precondition(b -> b.getRhs() == null)
                    .inLocal(8, Branch::getLhs)
                    .block(16, Branch::getBranchFalse)
                    .compile(Format.Format21t, (ctx, insn) -> new ImmutableInstruction21t(
                            branchToOpcode(insn.getType(), true, true),
                            ctx.inReg(insn.getLhs()),
                            ctx.offsetToBlock(insn.getBranchFalse())
                    ))
                    .continueTo(Branch::getBranchTrue);

    private static Opcode branchToOpcode(Branch.Type type, boolean inverted, boolean toZero) {
        switch (type) {
            case EQUAL: {
                if (inverted) {
                    return toZero ? Opcode.IF_NEZ : Opcode.IF_NE;
                } else {
                    return toZero ? Opcode.IF_EQZ : Opcode.IF_EQ;
                }
            }
            case LESS_THAN: {
                if (inverted) {
                    return toZero ? Opcode.IF_GEZ : Opcode.IF_GE;
                } else {
                    return toZero ? Opcode.IF_LTZ : Opcode.IF_LT;
                }
            }
            case GREATER_THAN: {
                if (inverted) {
                    return toZero ? Opcode.IF_LEZ : Opcode.IF_LE;
                } else {
                    return toZero ? Opcode.IF_GTZ : Opcode.IF_GT;
                }
            }
            default:
                throw new AssertionError();
        }
    }

    private static final InstructionTemplate<ArrayLoadStore> ARRAY_LOAD =
            new InstructionTemplate<>(ArrayLoadStore.class)
                    .precondition(insn -> insn.getType() == LoadStore.Type.LOAD)
                    .inLocal(8, ArrayLoadStore::getArray)
                    .inLocal(8, ArrayLoadStore::getIndex)
                    .outLocal(8, ArrayLoadStore::getValue)
                    .compile(Format.Format23x, (ctx, insn) -> new ImmutableInstruction23x(
                            arrayElementTypeToOpcode(insn.getElementType(), true),
                            ctx.outReg(insn.getValue()),
                            ctx.inReg(insn.getArray()),
                            ctx.inReg(insn.getIndex())
                    ));
    private static final InstructionTemplate<ArrayLoadStore> ARRAY_STORE =
            new InstructionTemplate<>(ArrayLoadStore.class)
                    .precondition(insn -> insn.getType() == LoadStore.Type.STORE)
                    .inLocal(8, ArrayLoadStore::getArray)
                    .inLocal(8, ArrayLoadStore::getIndex)
                    .inLocal(8, ArrayLoadStore::getValue)
                    .compile(Format.Format23x, (ctx, insn) -> new ImmutableInstruction23x(
                            arrayElementTypeToOpcode(insn.getElementType(), false),
                            ctx.inReg(insn.getValue()),
                            ctx.inReg(insn.getArray()),
                            ctx.inReg(insn.getIndex())
                    ));

    private static Opcode arrayElementTypeToOpcode(ArrayLoadStore.ElementType elementType, boolean load) {
        switch (elementType) {
            case BOOLEAN:
                return load ? Opcode.AGET_BOOLEAN : Opcode.APUT_BOOLEAN;
            case BYTE:
                return load ? Opcode.AGET_BYTE : Opcode.APUT_BYTE;
            case SHORT:
                return load ? Opcode.AGET_SHORT : Opcode.APUT_SHORT;
            case CHAR:
                return load ? Opcode.AGET_CHAR : Opcode.APUT_CHAR;
            case INT_FLOAT:
                return load ? Opcode.AGET : Opcode.APUT;
            case WIDE:
                return load ? Opcode.AGET_WIDE : Opcode.APUT_WIDE;
            case REFERENCE:
                return load ? Opcode.AGET_OBJECT : Opcode.APUT_OBJECT;
            default:
                throw new AssertionError();
        }
    }

    private static final InstructionTemplate<LoadStore> INSTANCE_LOAD =
            new InstructionTemplate<>(LoadStore.class)
                    .precondition(insn -> insn.getType() == LoadStore.Type.LOAD && insn.getInstance() != null)
                    .inLocal(4, LoadStore::getInstance)
                    .outLocal(4, LoadStore::getValue)
                    .compile(Format.Format22c, (ctx, insn) -> new ImmutableInstruction22c(
                            fieldTypeToOpcode(insn.getField(), true, false),
                            ctx.outReg(insn.getValue()),
                            ctx.inReg(insn.getInstance()),
                            ctx.field(insn.getField())
                    ));
    private static final InstructionTemplate<LoadStore> INSTANCE_STORE =
            new InstructionTemplate<>(LoadStore.class)
                    .precondition(insn -> insn.getType() == LoadStore.Type.STORE && insn.getInstance() != null)
                    .inLocal(4, LoadStore::getInstance)
                    .inLocal(4, LoadStore::getValue)
                    .compile(Format.Format22c, (ctx, insn) -> new ImmutableInstruction22c(
                            fieldTypeToOpcode(insn.getField(), false, false),
                            ctx.inReg(insn.getValue()),
                            ctx.inReg(insn.getInstance()),
                            ctx.field(insn.getField())
                    ));

    private static final InstructionTemplate<LoadStore> STATIC_LOAD =
            new InstructionTemplate<>(LoadStore.class)
                    .precondition(insn -> insn.getType() == LoadStore.Type.LOAD && insn.getInstance() == null)
                    .outLocal(8, LoadStore::getValue)
                    .compile(Format.Format21c, (ctx, insn) -> new ImmutableInstruction21c(
                            fieldTypeToOpcode(insn.getField(), true, true),
                            ctx.outReg(insn.getValue()),
                            ctx.field(insn.getField())
                    ));
    private static final InstructionTemplate<LoadStore> STATIC_STORE =
            new InstructionTemplate<>(LoadStore.class)
                    .precondition(insn -> insn.getType() == LoadStore.Type.STORE && insn.getInstance() == null)
                    .inLocal(8, LoadStore::getValue)
                    .compile(Format.Format21c, (ctx, insn) -> new ImmutableInstruction21c(
                            fieldTypeToOpcode(insn.getField(), false, true),
                            ctx.inReg(insn.getValue()),
                            ctx.field(insn.getField())
                    ));

    private static Opcode fieldTypeToOpcode(FieldMirror field, boolean load, boolean isStatic) {
        switch (field.getType().getType().getSort()) {
            case Type.BOOLEAN:
                if (isStatic) {
                    return load ? Opcode.SGET_BOOLEAN : Opcode.SPUT_BOOLEAN;
                } else {
                    return load ? Opcode.IGET_BOOLEAN : Opcode.IPUT_BOOLEAN;
                }
            case Type.BYTE:
                if (isStatic) {
                    return load ? Opcode.SGET_BYTE : Opcode.SPUT_BYTE;
                } else {
                    return load ? Opcode.IGET_BYTE : Opcode.IPUT_BYTE;
                }
            case Type.SHORT:
                if (isStatic) {
                    return load ? Opcode.SGET_SHORT : Opcode.SPUT_SHORT;
                } else {
                    return load ? Opcode.IGET_SHORT : Opcode.IPUT_SHORT;
                }
            case Type.CHAR:
                if (isStatic) {
                    return load ? Opcode.SGET_CHAR : Opcode.SPUT_CHAR;
                } else {
                    return load ? Opcode.IGET_CHAR : Opcode.IPUT_CHAR;
                }
            case Type.INT:
            case Type.FLOAT:
                if (isStatic) {
                    return load ? Opcode.SGET : Opcode.SPUT;
                } else {
                    return load ? Opcode.IGET : Opcode.IPUT;
                }
            case Type.LONG:
            case Type.DOUBLE:
                if (isStatic) {
                    return load ? Opcode.SGET_WIDE : Opcode.SPUT_WIDE;
                } else {
                    return load ? Opcode.IGET_WIDE : Opcode.IPUT_WIDE;
                }
            case Type.OBJECT:
            case Type.ARRAY:
                if (isStatic) {
                    return load ? Opcode.SGET_OBJECT : Opcode.SPUT_OBJECT;
                } else {
                    return load ? Opcode.IGET_OBJECT : Opcode.IPUT_OBJECT;
                }
            default:
                throw new AssertionError();
        }
    }

    static final List<InstructionTemplate<?>> TEMPLATES = Arrays.asList(
            MOVE, MOVE_FROM16, MOVE_16, SMART_MOVE,
            RETURN_VOID, RETURN,
            CONST, CONST_4, CONST_4_NULL, CONST_HIGH16, CONST_16, CONST_16_NULL,
            CONST_WIDE_16, CONST_WIDE_32, CONST_WIDE, CONST_WIDE_HIGH16,
            CONST_STRING, CONST_STRING_JUMBO, CONST_CLASS,
            MONITOR,
            CHECK_CAST, INSTANCE_OF,
            ARRAY_LENGTH,
            NEW_ARRAY_LENGTH,
            NEW_ARRAY_FILLED_NORMAL, NEW_ARRAY_FILLED_RANGE,
            FILL_ARRAY_DATA,
            THROW,
            GOTO,
            SWITCH,
            BINARY, BINARY_IN_PLACE, BINARY_LITERAL_16, BINARY_LITERAL_8,
            UNARY_OPERATION,
            IF_NORMAL, IF_INVERTED, IF_ZERO_NORMAL, IF_ZERO_INVERTED,
            ARRAY_LOAD, ARRAY_STORE,
            INSTANCE_LOAD, INSTANCE_STORE,
            STATIC_LOAD, STATIC_STORE,
            invoke(true, true, true),
            invoke(true, true, false),
            invoke(true, false, true),
            invoke(true, false, false),
            invoke(false, true, true),
            invoke(false, true, false),
            invoke(false, false, true),
            invoke(false, false, false)
    );
}
