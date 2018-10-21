package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.Try;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.jf.dexlib2.immutable.ImmutableExceptionHandler;
import org.jf.dexlib2.immutable.ImmutableTryBlock;

/**
 * @author yawkat
 */
final class InsnCatchHandlers {
    private final String[] types;
    private final InsnNode<?>[] nodes;

    InsnCatchHandlers(Try try_, Function<BasicBlock, InsnNode<?>> blockNodes) {
        List<Try.Catch> handlers = try_.getHandlers();
        types = new String[handlers.size()];
        nodes = new InsnNode[handlers.size()];

        for (int i = 0; i < handlers.size(); i++) {
            Try.Catch handler = handlers.get(i);
            types[i] = handler.getExceptionType() == null ? null : handler.getExceptionType().getType().getDescriptor();
            nodes[i] = blockNodes.apply(handler.getHandler());
        }
    }

    ImmutableTryBlock toTryBlock(InsnNode<?> start, InsnNode<?> end) {
        ImmutableList.Builder<ImmutableExceptionHandler> handlers = ImmutableList.builderWithExpectedSize(types.length);
        for (int i = 0; i < types.length; i++) {
            handlers.add(new ImmutableExceptionHandler(types[i], nodes[i].offset));
        }
        int startOffset = start.offset;
        return new ImmutableTryBlock(
                startOffset,
                (end.offset + end.getSize()) - startOffset,
                handlers.build()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof InsnCatchHandlers)) { return false; }
        InsnCatchHandlers that = (InsnCatchHandlers) o;
        return Arrays.equals(types, that.types) &&
               Arrays.equals(nodes, that.nodes);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(types);
        result = 31 * result + Arrays.hashCode(nodes);
        return result;
    }
}
