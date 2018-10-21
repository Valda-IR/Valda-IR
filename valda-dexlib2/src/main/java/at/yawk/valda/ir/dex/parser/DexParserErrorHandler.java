package at.yawk.valda.ir.dex.parser;

import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.NoSuchMemberException;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.annotation.AnnotationMember;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.Throw;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
public interface DexParserErrorHandler {
    static DexParserErrorHandler getDefault() {
        return DefaultDexParserErrorHandler.INSTANCE;
    }

    static DexParserErrorHandler getLenient() {
        return DefaultDexParserErrorHandler.Lenient.INSTANCE;
    }

    /**
     *
     * @param exception        The linkage error that was caught.
     * @param runtimeException The exception type that would be thrown at runtime when executing this code.
     * @return A list of instructions that should be inserted instead of the unlinkable instruction.
     * @throws NoSuchMemberException if the original error should be rethrown.
     */
    List<Instruction> handleInstructionLinkageError(
            Classpath classpath, NoSuchMemberException exception,
            Class<? extends IncompatibleClassChangeError> runtimeException
    ) throws NoSuchMemberException;

    /**
     * @param exception The linkage error that was caught.
     * @return The annotation key, or {@code null} if the property or array member should be dropped.
     * @throws NoSuchMemberException if the original error should be rethrown.
     */
    @Nullable
    MethodMirror handleAnnotationKeyLinkageError(NoSuchMemberException exception) throws NoSuchMemberException;

    /**
     * @param exception The linkage error that was caught.
     * @return The annotation value, or {@code null} if the property or array member should be dropped.
     * @throws NoSuchMemberException if the original error should be rethrown.
     */
    @Nullable
    AnnotationMember handleAnnotationValueLinkageError(NoSuchMemberException exception) throws NoSuchMemberException;
}

@Slf4j
class DefaultDexParserErrorHandler implements DexParserErrorHandler {
    static final DefaultDexParserErrorHandler INSTANCE = new DefaultDexParserErrorHandler();

    @Override
    public List<Instruction> handleInstructionLinkageError(
            Classpath classpath, NoSuchMemberException exception,
            Class<? extends IncompatibleClassChangeError> runtimeException
    ) throws NoSuchMemberException {
        throw exception;
    }

    @Nullable
    @Override
    public MethodMirror handleAnnotationKeyLinkageError(NoSuchMemberException exception) throws NoSuchMemberException {
        log.warn("Failed to resolve annotation member reference", exception);
        return null;
    }

    @Nullable
    @Override
    public AnnotationMember handleAnnotationValueLinkageError(NoSuchMemberException exception)
            throws NoSuchMemberException {
        log.warn("Annotation method resolution error, dropping member", exception);
        return null;
    }

    static final class Lenient extends DefaultDexParserErrorHandler {
        static final Lenient INSTANCE = new Lenient();

        @Override
        public List<Instruction> handleInstructionLinkageError(
                Classpath classpath, NoSuchMemberException exception,
                Class<? extends IncompatibleClassChangeError> runtimeException
        ) throws NoSuchMemberException {
            log.warn("Suppressing linkage error", exception);
            LocalVariable var = LocalVariable.reference("SuppressedLinkageError");
            return ImmutableList.of(
                    Invoke.builder()
                            .newInstance()
                            .method(classpath.getTypeMirror(Type.getType(runtimeException))
                                            .method("<init>", Type.getType("()V"), TriState.FALSE))
                            .returnValue(var)
                            .build(),
                    Throw.create(var)
            );
        }
    }
}