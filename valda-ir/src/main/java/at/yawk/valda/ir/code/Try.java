package at.yawk.valda.ir.code;

import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.TypeReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.NonNull;

/**
 * @author yawkat
 */
public final class Try {
    @NonNull private final Set<BasicBlock> enclosedBlocks = new HashSet<>();
    @NonNull private final List<Catch> handlers = new ArrayList<>();

    private boolean isClasspathLinked = false;

    public Try() {
    }

    void addEnclosedBlock(BasicBlock block) {
        boolean init = enclosedBlocks.isEmpty();
        if (!enclosedBlocks.add(block)) { throw new IllegalStateException(); }
        if (init) {
            for (Catch handler : handlers) {
                handler.handler.getReferencedBlock().addReference(handler.handler);
            }
        }
    }

    void removeEnclosedBlock(BasicBlock block) {
        if (!enclosedBlocks.remove(block)) { throw new IllegalStateException(); }
        if (enclosedBlocks.isEmpty()) {
            for (Catch handler : handlers) {
                handler.handler.getReferencedBlock().removeReference(handler.handler);
            }
        }
    }

    void linkClasspath() {
        if (!isClasspathLinked) {
            isClasspathLinked = true;
            for (Catch handler : handlers) {
                handler.linkClasspath();
            }
        }
    }

    void unlinkClasspath() {
        if (isClasspathLinked) {
            isClasspathLinked = false;
            for (Catch handler : handlers) {
                handler.unlinkClasspath();
            }
        }
    }

    public Set<BasicBlock> getEnclosedBlocks() {
        return Collections.unmodifiableSet(enclosedBlocks);
    }

    public List<Catch> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }

    public Catch addCatch(BasicBlock handler) {
        Catch c = new Catch(handler);
        handlers.add(c);
        return c;
    }

    public class Catch {
        @Nullable private TypeReference.CatchExceptionType exceptionType;
        @SuppressWarnings("NullableProblems")
        @NonNull private BlockReference.CatchHandler handler;

        Catch(BasicBlock handler) {
            setHandlerImpl(handler);
        }

        private void setHandlerImpl(BasicBlock handler) {
            this.handler = new BlockReference.CatchHandler(handler, this);
            if (!enclosedBlocks.isEmpty()) {
                handler.addReference(this.handler);
            }
        }

        public BasicBlock getHandler() {
            return handler.getReferencedBlock();
        }

        public void setHandler(BasicBlock handler) {
            BlockReference.CatchHandler oldHandler = this.handler;
            if (!enclosedBlocks.isEmpty()) {
                oldHandler.getReferencedBlock().removeReference(oldHandler);
                MethodBody body = enclosedBlocks.iterator().next().body;
                assert body != null;
                body.sweep();
            }
            setHandlerImpl(handler);
        }

        @Nullable
        public TypeMirror getExceptionType() {
            return exceptionType == null ? null : exceptionType.getReferencedType();
        }

        public void setExceptionType(@Nullable TypeMirror exceptionType) {
            if (this.exceptionType != null) {
                if (isClasspathLinked) {
                    linkClasspath();
                }
                this.exceptionType = null;
            }
            if (exceptionType != null) {
                this.exceptionType = SecretsHolder.secrets.newCatchExceptionType(exceptionType, this);
                if (isClasspathLinked) {
                    unlinkClasspath();
                }
            }
        }

        private void unlinkClasspath() {
            if (this.exceptionType != null) {
                exceptionType.getReferencedType().getReferences().remove(this.exceptionType);
            }
        }

        private void linkClasspath() {
            if (this.exceptionType != null) {
                this.exceptionType.getReferencedType().getReferences().add(this.exceptionType);
            }
        }

        public Try getTry() {
            return Try.this;
        }
    }
}
