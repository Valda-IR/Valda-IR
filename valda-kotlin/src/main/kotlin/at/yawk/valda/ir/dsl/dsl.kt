package at.yawk.valda.ir.dsl

import at.yawk.valda.ir.code.BasicBlock

/**
 * @author yawkat
 */
inline fun block(init: BlockBuilder.() -> Unit): BasicBlock {
    val block = BasicBlock.create()
    val builder = BlockBuilder(block)
    init(builder)
    builder.finish()
    return block
}

@DslMarker
annotation class BlockMarker