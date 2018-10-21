package at.yawk.valda.ir.dex.parser;

import java.util.Arrays;
import java.util.Collections;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction20t;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author yawkat
 */
public class InstructionListTest {
    @Test
    public void empty() {
        InstructionList list = new InstructionList(Collections.emptySet());
        Assert.assertEquals(list.getInstructionCount(), 0);
        Assert.assertEquals(list.getInstructionIndexAtOffset(0), 0);
        Assert.assertThrows(() -> list.getInstructionIndexAtOffset(1));
    }

    @Test
    public void single() {
        ImmutableInstruction10x nop = new ImmutableInstruction10x(Opcode.NOP);
        InstructionList list = new InstructionList(Collections.singletonList(nop));
        Assert.assertEquals(list.getInstructionCount(), 1);
        Assert.assertEquals(list.getEndOffset(), 1);
        Assert.assertEquals(list.getInstructionIndexAtOffset(0), 0);
        Assert.assertEquals(list.getInstructionIndexAtOffset(1), 1);
        Assert.assertThrows(() -> list.getInstructionIndexAtOffset(2));
        Assert.assertEquals(list.getInstruction(0), nop);
        Assert.assertEquals(list.getInstructionAtOffset(0), nop);
        Assert.assertEquals(list.getOffset(0), 0);
    }

    @Test
    public void more() {
        ImmutableInstruction10x nop1 = new ImmutableInstruction10x(Opcode.NOP);
        ImmutableInstruction20t jmp = new ImmutableInstruction20t(Opcode.GOTO_16, -1);
        ImmutableInstruction10x nop2 = new ImmutableInstruction10x(Opcode.NOP);
        InstructionList list = new InstructionList(Arrays.asList(nop1, jmp, nop2));

        Assert.assertEquals(list.getInstructionCount(), 3);
        Assert.assertEquals(list.getEndOffset(), 4);
        Assert.assertEquals(list.getInstructionIndexAtOffset(0), 0);
        Assert.assertEquals(list.getInstructionIndexAtOffset(1), 1);
        Assert.assertThrows(() -> list.getInstructionIndexAtOffset(2));
        Assert.assertEquals(list.getInstructionIndexAtOffset(3), 2);
        Assert.assertEquals(list.getInstructionIndexAtOffset(4), 3);
        Assert.assertThrows(() -> list.getInstructionIndexAtOffset(5));
        Assert.assertEquals(list.getInstruction(0), nop1);
        Assert.assertEquals(list.getInstruction(1), jmp);
        Assert.assertEquals(list.getInstruction(2), nop2);
        Assert.assertEquals(list.getInstructionAtOffset(0), nop1);
        Assert.assertEquals(list.getInstructionAtOffset(1), jmp);
        Assert.assertThrows(() -> list.getInstructionAtOffset(2));
        Assert.assertEquals(list.getInstructionAtOffset(3), nop2);
        Assert.assertEquals(list.getOffset(0), 0);
        Assert.assertEquals(list.getOffset(1), 1);
        Assert.assertEquals(list.getOffset(2), 3);
    }
}