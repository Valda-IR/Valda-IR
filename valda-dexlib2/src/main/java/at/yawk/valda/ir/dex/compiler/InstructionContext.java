package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.LocalVariable;
import org.jf.dexlib2.iface.instruction.PayloadInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;

/**
 * @author yawkat
 */
interface InstructionContext {
    int inReg(LocalVariable variable);

    int outReg(LocalVariable variable);

    int outRegNoOverlap(LocalVariable variable);

    int tmpReg(LocalVariable variable);

    default StringReference string(String s) {
        return new ImmutableStringReference(s);
    }

    default TypeReference type(TypeMirror type) {
        return new ImmutableTypeReference(type.getType().getDescriptor());
    }

    default MethodReference method(MethodMirror method) {
        return References.method(method);
    }

    default FieldReference field(FieldMirror field) {
        return References.field(field);
    }

    int offsetToPayload(PayloadInstruction payload);

    int offsetToBlock(BasicBlock block);
}
