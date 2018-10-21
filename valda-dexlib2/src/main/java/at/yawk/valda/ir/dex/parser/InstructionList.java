package at.yawk.valda.ir.dex.parser;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.Getter;
import lombok.NonNull;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.jf.dexlib2.iface.instruction.Instruction;

/**
 * @author yawkat
 */
final class InstructionList implements Iterable<Instruction> {
    private final List<Instruction> instructions;
    @Getter private final int endOffset;
    private final IntList instructionOffsets;

    InstructionList(Iterable<? extends Instruction> instructions) {
        this.instructions = ImmutableList.copyOf(instructions);

        int off = 0;
        MutableIntList instructionOffsets = IntLists.mutable.empty();
        for (Instruction instruction : this.instructions) {
            instructionOffsets.add(off);
            off += instruction.getCodeUnits();
        }
        this.endOffset = off;
        this.instructionOffsets = instructionOffsets;
    }

    public int getInstructionCount() {
        return instructions.size();
    }

    public Instruction getInstruction(int index) {
        return instructions.get(index);
    }

    public Instruction getInstructionAtOffset(int offset) {
        int i = getInstructionIndexAtOffset(offset);
        return getInstruction(i);
    }

    public int getInstructionIndexAtOffset(int offset) {
        if (offset == endOffset) {
            return getInstructionCount();
        }
        int i = instructionOffsets.binarySearch(offset);
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return i;
    }

    public int getOffset(int index) {
        return instructionOffsets.get(index);
    }

    @NonNull
    @Override
    public Iterator<Instruction> iterator() {
        return instructions.iterator();
    }
}
