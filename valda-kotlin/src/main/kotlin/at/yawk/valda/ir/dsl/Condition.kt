@file:Suppress("UNUSED_PARAMETER")
@file:JvmName("Conditions")

package at.yawk.valda.ir.dsl

import at.yawk.valda.ir.code.Branch
import at.yawk.valda.ir.code.LocalVariable

/**
 * @author yawkat
 */
data class Condition(val type: Branch.Type, val lhs: LocalVariable, val rhs: LocalVariable?, val invert: Boolean)

infix fun LocalVariable.lt(rhs: LocalVariable) = Condition(Branch.Type.LESS_THAN, this, rhs, false)
infix fun LocalVariable.eq(rhs: LocalVariable) = Condition(Branch.Type.EQUAL, this, rhs, false)
infix fun LocalVariable.gt(rhs: LocalVariable) = Condition(Branch.Type.GREATER_THAN, this, rhs, false)
infix fun LocalVariable.ge(rhs: LocalVariable) = Condition(Branch.Type.LESS_THAN, this, rhs, true)
infix fun LocalVariable.ne(rhs: LocalVariable) = Condition(Branch.Type.EQUAL, this, rhs, true)
infix fun LocalVariable.le(rhs: LocalVariable) = Condition(Branch.Type.GREATER_THAN, this, rhs, true)

object Zero

@JvmField
val ZERO = Zero

infix fun LocalVariable.lt(rhs: Zero) = Condition(Branch.Type.LESS_THAN, this, null, false)
infix fun LocalVariable.eq(rhs: Zero) = Condition(Branch.Type.EQUAL, this, null, false)
infix fun LocalVariable.gt(rhs: Zero) = Condition(Branch.Type.GREATER_THAN, this, null, false)
infix fun LocalVariable.ge(rhs: Zero) = Condition(Branch.Type.LESS_THAN, this, null, true)
infix fun LocalVariable.ne(rhs: Zero) = Condition(Branch.Type.EQUAL, this, null, true)
infix fun LocalVariable.le(rhs: Zero) = Condition(Branch.Type.GREATER_THAN, this, null, true)