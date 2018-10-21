package at.yawk.valda.analyze

import at.yawk.valda.ir.code.BasicBlock
import at.yawk.valda.ir.code.BinaryOperation
import at.yawk.valda.ir.code.Const
import at.yawk.valda.ir.code.LiteralBinaryOperation
import at.yawk.valda.ir.code.LocalVariable
import at.yawk.valda.ir.code.MethodBody
import at.yawk.valda.ir.code.Return
import at.yawk.valda.ir.code.Try
import at.yawk.valda.ir.dsl.Zero
import at.yawk.valda.ir.dsl.block
import at.yawk.valda.ir.dsl.ne
import org.eclipse.collections.api.set.primitive.ImmutableIntSet
import org.eclipse.collections.impl.factory.primitive.IntSets
import org.testng.Assert
import org.testng.annotations.Test

/**
 * @author yawkat
 */
@Suppress("RemoveRedundantBackticks")
class AnalyzerTest {
    @Test
    fun simple() {
        val v = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(v, 5))
            instruction(Return.create(v))
        }

        val analyzer = Analyzer(IntInterpreter())
        analyzer.interpret(MethodBody(block))

        val returnEntry = analyzer.getNodes(block).last()
        Assert.assertEquals(returnEntry.instruction, Return.create(v))
        Assert.assertEquals(returnEntry.getSingleInput(v), IntSets.immutable.of(5))
    }

    @Test
    fun `branch on parameter`() {
        val v = LocalVariable.narrow()
        val flag = LocalVariable.narrow()
        var lastBlock: BasicBlock? = null
        val block = block {
            ifBlock(flag ne Zero) {
                instruction(Const.createNarrow(v, 1))
            } elseBlock {
                instruction(Const.createNarrow(v, 2))
            }
            lastBlock = label()
            instruction(Return.create(v))
        }
        val methodBody = MethodBody(block)
        methodBody.parameters.add(flag)

        val analyzer = Analyzer(object : IntInterpreter() {
            override fun getParameterValue(variable: LocalVariable): ImmutableIntSet {
                return IntSets.immutable.of(0, 1)
            }
        })
        analyzer.interpret(methodBody)

        val returnEntry = analyzer.getNodes(lastBlock).last()
        Assert.assertEquals(returnEntry.instruction, Return.create(v))
        Assert.assertEquals(returnEntry.getSingleInput(v), IntSets.immutable.of(1, 2))
    }

    @Test
    fun `try`() {
        val v = LocalVariable.narrow()
        val w = LocalVariable.narrow()

        val block = block {
            instruction(Const.createNarrow(v, 5))
            instruction(BinaryOperation.builder().type(BinaryOperation.Type.DIV_INT).lhs(v).rhs(v).destination(w).build())
            instruction(Const.createNarrow(v, 6))
            instruction(BinaryOperation.builder().type(BinaryOperation.Type.DIV_INT).lhs(v).rhs(v).destination(w).build())
            instruction(Return.create(v))
        }
        val catch = block {
            instruction(Return.create(v))
        }
        block.`try` = Try().also { it.addCatch(catch) }

        val analyzer = Analyzer(IntInterpreter())
        analyzer.interpret(MethodBody(block))

        Assert.assertEquals(
                analyzer.getNodes(block).last().getSingleInput(v), IntSets.immutable.of(6))
        Assert.assertEquals(
                analyzer.getNodes(catch).last().getSingleInput(v), IntSets.immutable.of(5, 6))
    }

    @Test(timeOut = 5000) // with the old version this would time out
    fun `repeated execution with improper merging`() {
        val i = LocalVariable.narrow()
        val lim = LocalVariable.narrow()
        lateinit var retBlock: BasicBlock
        val block = block {
            instruction(Const.createNarrow(i, 0))
            instruction(Const.createNarrow(lim, 5))
            whileLoop(i ne lim) {
                instruction(LiteralBinaryOperation.builder().type(LiteralBinaryOperation.Type.ADD).lhs(i).rhs(1)
                        .destination(i).build())
            }
            retBlock = label()
            instruction(Return.create(i))
        }
        val analyzer = Analyzer(SinglePassIntInterpreter())
        analyzer.interpret(MethodBody(block))

        Assert.assertEquals(
                analyzer.getNodes(retBlock).last().getSingleInput(i), IntSets.immutable.of(5))
    }
}