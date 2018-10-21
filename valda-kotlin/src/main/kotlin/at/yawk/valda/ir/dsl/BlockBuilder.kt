package at.yawk.valda.ir.dsl

import at.yawk.valda.ir.TypeMirror
import at.yawk.valda.ir.code.BasicBlock
import at.yawk.valda.ir.code.Branch
import at.yawk.valda.ir.code.GoTo
import at.yawk.valda.ir.code.Instruction
import at.yawk.valda.ir.code.LocalVariable
import at.yawk.valda.ir.code.Try
import java.util.function.Consumer

/**
 * @author yawkat
 */
@BlockMarker
class BlockBuilder constructor(
        firstBlock: BasicBlock,
        private val try_: Try? = null
) {
    companion object {
        @JvmStatic
        fun block(init: Consumer<BlockBuilder>) = at.yawk.valda.ir.dsl.block { init.accept(this) }
    }

    private var currentBlock = firstBlock
    private var done = false

    private fun createBlock(): BasicBlock {
        val block = BasicBlock.create()
        block.`try` = try_
        return block
    }

    private fun createSubBuilder(firstBlock: BasicBlock, try_: Try? = this.try_) = BlockBuilder(firstBlock, try_)

    fun ifBlock(condition: Condition, init: BlockBuilder.() -> Unit): IfClosure {
        checkBuilding()
        val branchTrue = createBlock()
        val branchTrueBuilder = createSubBuilder(branchTrue)
        init(branchTrueBuilder)
        val nextBlock = createBlock()
        branchTrueBuilder.finish(nextBlock)
        val branch = Branch.builder()
                .type(condition.type).rhs(condition.rhs).lhs(condition.lhs)
                .branchTrue(if (condition.invert) nextBlock else branchTrue)
                .branchFalse(if (condition.invert) branchTrue else nextBlock)
                .build()
        instruction(branch)
        currentBlock = nextBlock
        return IfClosure(branch, condition.invert)
    }

    fun ifBlock(condition: Condition, init: Consumer<BlockBuilder>): IfClosure =
            ifBlock(condition) { init.accept(this) }

    fun tryBlock(init: BlockBuilder.() -> Unit): TryClosure {
        val block = label()
        if (this.try_ != null) throw IllegalStateException("Try block nesting is not yet supported")
        val `try` = Try()
        block.`try` = `try`
        val builder = createSubBuilder(block, `try`)
        init(builder)
        val end = createBlock()
        builder.finish(end)
        currentBlock = end
        return TryClosure(`try`, end)
    }

    fun tryBlock(init: Consumer<BlockBuilder>): TryClosure = tryBlock { init.accept(this) }

    fun label(): BasicBlock {
        if (!currentBlock.instructions.isEmpty()) {
            val next = createBlock()
            currentBlock.addInstruction(GoTo.create(next))
            currentBlock = next
        }
        return currentBlock
    }

    fun instruction(instruction: Instruction) {
        checkBuilding()
        currentBlock.addInstruction(instruction)
    }

    fun finish() = finish(null)

    private fun finish(continueTo: BasicBlock?) {
        if (continueTo == null) {
            if (!currentBlock.isTerminated) {
                throw IllegalStateException("Block not terminated")
            }
        } else {
            if (!currentBlock.isTerminated) {
                goto(continueTo)
            }
        }
        done = true
    }

    private fun checkBuilding() {
        if (done) throw IllegalStateException("Already done")
    }

    inline fun loop(init: BlockBuilder.() -> Unit) {
        val start = label()
        init()
        goto(start)
    }

    fun loop(init: Consumer<BlockBuilder>) {
        loop { init.accept(this) }
    }

    fun goto(block: BasicBlock) {
        instruction(GoTo.create(block))
    }

    inline fun whileLoop(condition: Condition, crossinline init: BlockBuilder.() -> Unit) {
        val start = label()
        ifBlock(condition) {
            init()
            goto(start)
        }
    }

    fun whileLoop(condition: Condition, init: Consumer<BlockBuilder>) {
        whileLoop(condition) { init.accept(this) }
    }

    inner class IfClosure internal constructor(private val branch: Branch, private val invert: Boolean) {
        infix fun elseBlock(init: BlockBuilder.() -> Unit) {
            val branchFalse = createBlock()
            val branchFalseBuilder = createSubBuilder(branchFalse)
            init(branchFalseBuilder)
            branchFalseBuilder.finish(if (invert) branch.branchTrue else branch.branchFalse)
            if (invert) {
                branch.branchTrue = branchFalse
            } else {
                branch.branchFalse = branchFalse
            }
        }

        fun elseBlock(init: Consumer<BlockBuilder>) = elseBlock { init.accept(this) }
    }

    inner class TryClosure internal constructor(val `try`: Try,
                                                private val end: BasicBlock) {
        fun catchBlock(type: TypeMirror? = null, exceptionVariable: LocalVariable? = null,
                       init: BlockBuilder.() -> Unit): TryClosure {
            val c = createBlock()
            c.exceptionVariable = exceptionVariable
            val builder = createSubBuilder(c)
            init(builder)
            builder.finish(end)
            return catchBlock(type, c)
        }

        @JvmOverloads
        fun catchBlock(type: TypeMirror? = null, exceptionVariable: LocalVariable? = null,
                       init: Consumer<BlockBuilder>): TryClosure {
            return catchBlock(type, exceptionVariable) { init.accept(this) }
        }

        fun catchBlock(type: TypeMirror? = null, block: BasicBlock): TryClosure {
            `try`.addCatch(block).exceptionType = type
            return this
        }

        infix fun catchAll(init: BlockBuilder.() -> Unit) = catchBlock(init = init)

        fun catchAll(init: Consumer<BlockBuilder>) = catchAll { init.accept(this) }
    }
}