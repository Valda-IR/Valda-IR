package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.code.LocalVariable;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * @author yawkat
 */
@AllArgsConstructor
class RegisterAllocationRequest {
    int registerBits;
    @NonNull final LocalVariable variable;
    boolean in;
    boolean out;

    int size() {
        return NaiveCodeCompiler.width(variable);
    }
}
