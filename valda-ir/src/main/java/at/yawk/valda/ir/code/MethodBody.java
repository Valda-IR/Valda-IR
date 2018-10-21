package at.yawk.valda.ir.code;

import at.yawk.valda.ir.Secrets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * @author yawkat
 */
public final class MethodBody {
    final Set<BasicBlock> blocks = new HashSet<>();
    private BlockReference.EntryPoint entryPoint = null;
    @NonNull @Getter @Setter private List<LocalVariable> parameters = new ArrayList<>();
    @Getter private boolean classpathLinked;

    private long nextGeneration = 0;

    public MethodBody(@NonNull BasicBlock entryPoint) {
        setEntryPoint(entryPoint);
    }

    public void setEntryPoint(@NonNull BasicBlock entryPoint) {
        if (this.entryPoint != null) {
            this.entryPoint.getReferencedBlock().removeReference(this.entryPoint);
        }
        this.entryPoint = new BlockReference.EntryPoint(entryPoint, this);
        entryPoint.addReference(this.entryPoint);
        sweep();
    }

    @NonNull
    public BasicBlock getEntryPoint() {
        return entryPoint.getReferencedBlock();
    }

    public Set<BasicBlock> getBlocks() {
        return Collections.unmodifiableSet(blocks);
    }

    /**
     * @deprecated Internal use.
     */
    @SuppressWarnings({ "unused", "NewMethodNamingConvention", "DeprecatedIsStillUsed" })
    @Deprecated
    public void _linkClasspath(@NonNull Secrets secrets, boolean link) {
        if (classpathLinked == link) { throw new IllegalStateException(); }
        classpathLinked = link;
        for (BasicBlock block : blocks) {
            if (link) {
                block.linkClasspath();
            } else {
                block.unlinkClasspath();
            }
        }
    }

    void sweep() {
        long gen = this.nextGeneration++;
        sweep(gen, getEntryPoint());
        for (Iterator<BasicBlock> iterator = blocks.iterator(); iterator.hasNext(); ) {
            BasicBlock block = iterator.next();
            if (block.generation != gen) {
                iterator.remove();
                block.body = null;
                block.onUnreachable(isClasspathLinked());
            }
        }
    }

    private void sweep(long gen, BasicBlock block) {
        if (block.body == null) {
            block.onReachable(this);
        } else if (block.generation == gen) {
            return;
        }
        //noinspection ObjectEquality
        assert block.body == this;
        block.generation = gen;
        if (block.isTerminated()) {
            for (BasicBlock successor : block.getTerminatingInstruction().getSuccessors()) {
                sweep(gen, successor);
            }
        }
        Try try_ = block.getTry();
        if (try_ != null) {
            for (Try.Catch handler : try_.getHandlers()) {
                sweep(gen, handler.getHandler());
            }
        }
    }

    @Override
    public String toString() {
        return "MethodBody" + blocks;
    }
}
