package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.TerminatingInstruction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.Value;
import org.jf.dexlib2.Format;

/**
 * @author yawkat
 */
@SuppressWarnings({ "SameParameterValue", "UnusedReturnValue" })
final class InstructionTemplate<I extends Instruction> {
    final Class<I> type;
    final List<Predicate<I>> preconditions = new ArrayList<>();

    final List<Locals<I>> inLocals = new ArrayList<>();
    final List<Locals<I>> outLocals = new ArrayList<>();
    final List<Locals<I>> outLocalsNoOverlap = new ArrayList<>();
    final List<Locals<I>> tmpLocals = new ArrayList<>();
    @Nullable SameRegister<I> sameRegister = null;

    Contiguous<I> contiguous = null;

    /**
     * This is unused at the moment. If these constraints would be supported, at.yawk.valda.obfuscator.apk.ApkApkFile
     * could support string pools larger than 2^16
     */
    final List<StringRef<I>> stringRefs = new ArrayList<>();
    final List<BlockRefs<I>> blockRefs = new ArrayList<>();
    Function<I, BasicBlock> continueTo = null;
    Format[] formats;
    BiFunction<InstructionContext, I, org.jf.dexlib2.iface.instruction.Instruction[]> compile;

    InstructionTemplate(Class<I> type) {
        this.type = type;
    }

    InstructionTemplate<I> precondition(Predicate<I> predicate) {
        preconditions.add(predicate);
        return this;
    }

    InstructionTemplate<I> inLocal(int widthBits, Function<I, LocalVariable> variable) {
        return inLocals(widthBits, i -> Collections.singletonList(variable.apply(i)));
    }

    InstructionTemplate<I> inLocals(int widthBits, Function<I, List<LocalVariable>> variable) {
        inLocals.add(new Locals<>(widthBits, variable));
        return this;
    }

    InstructionTemplate<I> contiguous(Function<I, LocalVariable> firstOut, Function<I, List<LocalVariable>> in) {
        assert contiguous == null;
        contiguous = new Contiguous<>(firstOut, in);
        return this;
    }

    InstructionTemplate<I> outLocal(int widthBits, Function<I, LocalVariable> variable) {
        outLocals.add(new Locals<>(widthBits, i -> Collections.singletonList(variable.apply(i))));
        return this;
    }

    InstructionTemplate<I> tmpLocal(int widthBits, LocalVariable variable) {
        tmpLocals.add(new Locals<>(widthBits, i -> Collections.singletonList(variable)));
        return this;
    }

    InstructionTemplate<I> outLocalNoOverlap(int widthBits, Function<I, LocalVariable> variable) {
        outLocalsNoOverlap.add(new Locals<>(widthBits, i -> Collections.singletonList(variable.apply(i))));
        return this;
    }

    InstructionTemplate<I> string(int widthBits, Function<I, String> string) {
        stringRefs.add(new StringRef<>(widthBits, string));
        return this;
    }

    InstructionTemplate<I> block(int widthBits, Function<I, BasicBlock> block) {
        return blocks(widthBits, i -> Collections.singleton(block.apply(i)));
    }

    InstructionTemplate<I> blocks(int widthBits, Function<I, Collection<BasicBlock>> blocks) {
        blockRefs.add(new BlockRefs<>(widthBits, blocks));
        return this;
    }

    InstructionTemplate<I> compile(
            Format format,
            BiFunction<InstructionContext, I, org.jf.dexlib2.iface.instruction.Instruction> insn
    ) {
        return compile(new Format[]{ format },
                       (ctx, i) -> new org.jf.dexlib2.iface.instruction.Instruction[]{ insn.apply(ctx, i) });
    }

    InstructionTemplate<I> compile(
            Format[] formats,
            BiFunction<InstructionContext, I, org.jf.dexlib2.iface.instruction.Instruction[]> insn
    ) {
        assert this.formats == null;
        assert this.compile == null;
        this.formats = formats;
        this.compile = insn;
        return this;
    }

    InstructionTemplate<I> continueTo(Function<I, BasicBlock> block) {
        assert TerminatingInstruction.class.isAssignableFrom(type);
        assert continueTo == null;
        continueTo = block;
        return this;
    }

    InstructionTemplate<I> sameRegister(Function<I, LocalVariable> a, Function<I, LocalVariable> b) {
        assert sameRegister == null;
        sameRegister = new SameRegister<>(a, b);
        return this;
    }

    @Value
    static final class Locals<I> {
        int registerBits;
        Function<I, List<LocalVariable>> locals;
    }

    @Value
    static final class Contiguous<I> {
        @Nullable Function<I, LocalVariable> firstOut;
        Function<I, List<LocalVariable>> ins;
    }

    @Value
    static final class SameRegister<I> {
        Function<I, LocalVariable> a;
        Function<I, LocalVariable> b;
    }

    @Value
    static final class StringRef<I> {
        int referenceWidth;
        Function<I, String> string;
    }

    @Value
    static final class BlockRefs<I> {
        int referenceWidth;
        Function<I, Collection<BasicBlock>> blocks;
    }
}
