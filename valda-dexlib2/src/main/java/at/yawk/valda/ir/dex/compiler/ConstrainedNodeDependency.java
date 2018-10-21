package at.yawk.valda.ir.dex.compiler;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * @author yawkat
 */
@ToString
@RequiredArgsConstructor
final class ConstrainedNodeDependency {
    final int width;
    final InsnNode<?> from;
    final InsnNode<?> to;

    int latestOffset = -1;
    InsnNode<?> latestNode;
}
