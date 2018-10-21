package at.yawk.valda.ir.dsl

import at.yawk.valda.ir.code.Branch
import at.yawk.valda.ir.code.Const
import at.yawk.valda.ir.code.GoTo
import at.yawk.valda.ir.code.LiteralBinaryOperation
import at.yawk.valda.ir.code.LocalVariable
import at.yawk.valda.ir.code.Return
import org.testng.Assert
import org.testng.annotations.Test

/**
 * @author yawkat
 */
class DslTest {
    @Test
    fun simple() {
        val v = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(v, 1))
            instruction(Return.create(v))
        }

        Assert.assertEquals(
                block.instructions,
                listOf(
                        Const.createNarrow(v, 1),
                        Return.create(v)
                )
        )
    }

    @Test(expectedExceptions = [IllegalArgumentException::class])
    fun noDoubleReturn() {
        val v = LocalVariable.narrow()
        block {
            instruction(Const.createNarrow(v, 1))
            instruction(Return.create(v))
            instruction(Return.create(v))
        }
    }

    @Test(expectedExceptions = [IllegalStateException::class])
    fun noMissingReturn() {
        val v = LocalVariable.narrow()
        block {
            instruction(Const.createNarrow(v, 1))
        }
    }

    @Test
    fun `if`() {
        val v = LocalVariable.narrow()
        val w = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(v, 1))
            instruction(Const.createNarrow(w, 2))
            ifBlock(v lt w) {
                instruction(Const.createNarrow(v, 5))
            }
            instruction(Return.create(v))
        }

        val tr = (block.terminatingInstruction as Branch).branchTrue
        val fa = (block.terminatingInstruction as Branch).branchFalse
        Assert.assertEquals(block.instructions,
                listOf(Const.createNarrow(v, 1), Const.createNarrow(w, 2),
                        Branch.builder()
                                .type(Branch.Type.LESS_THAN)
                                .lhs(v).rhs(w).branchTrue(tr).branchFalse(fa)
                                .build()
                ))
        Assert.assertEquals(tr.instructions, listOf(Const.createNarrow(v, 5), GoTo.create(fa)))
        Assert.assertEquals(fa.instructions, listOf(Return.create(v)))
    }

    @Test
    fun `if invert`() {
        val v = LocalVariable.narrow()
        val w = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(v, 1))
            instruction(Const.createNarrow(w, 2))
            ifBlock(v le w) {
                instruction(Const.createNarrow(v, 5))
            }
            instruction(Return.create(v))
        }

        val tr = (block.terminatingInstruction as Branch).branchFalse
        val fa = (block.terminatingInstruction as Branch).branchTrue
        Assert.assertEquals(block.instructions,
                listOf(Const.createNarrow(v, 1), Const.createNarrow(w, 2),
                        Branch.builder().type(Branch.Type.GREATER_THAN)
                                .lhs(v).rhs(w).branchFalse(tr).branchTrue(fa).build()))
        Assert.assertEquals(tr.instructions, listOf(Const.createNarrow(v, 5), GoTo.create(fa)))
        Assert.assertEquals(fa.instructions, listOf(Return.create(v)))
    }

    @Test
    fun `if else`() {
        val v = LocalVariable.narrow()
        val w = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(v, 1))
            instruction(Const.createNarrow(w, 2))
            ifBlock(v lt w) {
                instruction(Const.createNarrow(v, 5))
            } elseBlock {
                instruction(Const.createNarrow(v, 6))
            }
            instruction(Return.create(v))
        }

        val tr = (block.terminatingInstruction as Branch).branchTrue
        val fa = (block.terminatingInstruction as Branch).branchFalse
        val end = ((block.terminatingInstruction as Branch).branchFalse.terminatingInstruction as GoTo).target
        Assert.assertEquals(block.instructions,
                listOf(Const.createNarrow(v, 1), Const.createNarrow(w, 2),
                        Branch.builder()
                                .type(Branch.Type.LESS_THAN).lhs(v).rhs(w).branchTrue(tr).branchFalse(fa)
                                .build()))
        Assert.assertEquals(tr.instructions, listOf(Const.createNarrow(v, 5), GoTo.create(end)))
        Assert.assertEquals(fa.instructions, listOf(Const.createNarrow(v, 6), GoTo.create(end)))
        Assert.assertEquals(end.instructions, listOf(Return.create(v)))
    }

    @Test
    fun loop() {
        val v = LocalVariable.narrow()
        val block = block {
            loop {
                instruction(Const.createNarrow(v, 1))
            }
        }

        Assert.assertEquals(block.instructions,
                listOf(Const.createNarrow(v, 1), GoTo.create(block)))
    }

    @Test
    fun `while`() {
        val v = LocalVariable.narrow()
        val w = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(v, 1))
            instruction(Const.createNarrow(w, 100))
            whileLoop(v lt w) {
                instruction(LiteralBinaryOperation.builder()
                        .type(LiteralBinaryOperation.Type.ADD).lhs(v).rhs(1).destination(v).build())
            }
            instruction(Return.create(v))
        }

        val cond = (block.terminatingInstruction as GoTo).target
        val body = (cond.terminatingInstruction as Branch).branchTrue
        val end = (cond.terminatingInstruction as Branch).branchFalse
        Assert.assertEquals(block.instructions,
                listOf(Const.createNarrow(v, 1), Const.createNarrow(w, 100), GoTo.create(cond)))
        Assert.assertEquals(cond.instructions,
                listOf(Branch.builder().type(Branch.Type.LESS_THAN).lhs(v).rhs(w).branchTrue(body).branchFalse(end)))
        Assert.assertEquals(body.instructions,
                listOf(LiteralBinaryOperation.builder()
                        .type(LiteralBinaryOperation.Type.ADD).lhs(v).rhs(1).destination(v).build(), GoTo.create(cond)))
        Assert.assertEquals(end.instructions,
                listOf(Return.create(v)))
    }

    @Test
    fun `try`() {
        val v = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(v, 100))
            tryBlock {
                instruction(Const.createNarrow(v, 1))
                ifBlock(v eq Zero) {
                    instruction(Const.createNarrow(v, 2))
                }
            } catchAll {
                instruction(Const.createNarrow(v, 5))
            }
            instruction(Return.create(v))
        }

        //CodePrinter(MethodBody(block)).print("", System.out)

        val enclosed = block.terminatingInstruction.successors.single()
        val enclosedIfTrue = (enclosed.terminatingInstruction as Branch).branchTrue
        // the intermediate block just leaves the try immediately
        val intermediate = (enclosed.terminatingInstruction as Branch).branchFalse
        val ret = intermediate.terminatingInstruction.successors.single()

        Assert.assertEquals(block.instructions, listOf(Const.createNarrow(v, 100), GoTo.create(enclosed)))
        Assert.assertEquals(enclosed.instructions,
                listOf(Const.createNarrow(v, 1),
                        Branch.builder().type(Branch.Type.EQUAL).lhs(v).rhsZero().branchTrue(enclosedIfTrue)
                                .branchFalse(intermediate).build()))
        Assert.assertEquals(enclosedIfTrue.instructions, listOf(Const.createNarrow(v, 2), GoTo.create(intermediate)))
        Assert.assertEquals(intermediate.instructions, listOf(GoTo.create(ret)))
        Assert.assertEquals(ret.instructions, listOf(Return.create(v)))

        for (b in listOf(enclosed, enclosedIfTrue, intermediate)) {
            Assert.assertEquals(b.`try`?.handlers?.single()?.exceptionType, null)
            val handler = b.`try`?.handlers?.single()?.handler
            Assert.assertNotNull(handler)
            Assert.assertEquals(handler?.instructions, listOf(Const.createNarrow(v, 5), GoTo.create(ret)))
        }
    }
}