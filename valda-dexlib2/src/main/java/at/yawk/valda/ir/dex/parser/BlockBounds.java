package at.yawk.valda.ir.dex.parser;

import lombok.Value;

/**
 * @author yawkat
 */
@Value
class BlockBounds {
    private final int startIndex;
    private final int instructionCount;
}
