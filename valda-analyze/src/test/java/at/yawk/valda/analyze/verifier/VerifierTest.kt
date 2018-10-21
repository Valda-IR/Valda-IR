package at.yawk.valda.analyze.verifier

import at.yawk.valda.Art
import at.yawk.valda.SmaliUtils
import at.yawk.valda.analyze.Analyzer
import at.yawk.valda.ir.Classpath
import at.yawk.valda.ir.ExternalTypeMirror
import at.yawk.valda.ir.FieldMirror
import at.yawk.valda.ir.LocalClassMirror
import at.yawk.valda.ir.MethodMirror
import at.yawk.valda.ir.TriState
import at.yawk.valda.ir.TypeMirror
import at.yawk.valda.ir.Types
import at.yawk.valda.ir.code.BasicBlock
import at.yawk.valda.ir.code.CheckCast
import at.yawk.valda.ir.code.Const
import at.yawk.valda.ir.code.InstanceOf
import at.yawk.valda.ir.code.Invoke
import at.yawk.valda.ir.code.LiteralBinaryOperation
import at.yawk.valda.ir.code.LoadStore
import at.yawk.valda.ir.code.LocalVariable
import at.yawk.valda.ir.code.MethodBody
import at.yawk.valda.ir.code.Move
import at.yawk.valda.ir.code.Return
import at.yawk.valda.ir.code.Throw
import at.yawk.valda.ir.code.UnaryOperation
import at.yawk.valda.ir.dex.compiler.DexCompiler
import at.yawk.valda.ir.dsl.Zero
import at.yawk.valda.ir.dsl.block
import at.yawk.valda.ir.dsl.eq
import at.yawk.valda.ir.printer.CodePrinter
import org.jf.dexlib2.writer.pool.DexPool
import org.objectweb.asm.Type
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.BufferedReader
import java.io.Closeable
import java.io.Reader
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import kotlin.jvm.internal.Intrinsics

/**
 * @author yawkat
 */
class VerifierTest {
    lateinit var classpath: Classpath
    lateinit var mainClass: LocalClassMirror

    @BeforeMethod
    fun setUp() {
        classpath = Classpath()
        mainClass = classpath.createClass(Type.getType("LMain;"))
    }

    @DataProvider
    fun artTest() = arrayOf(arrayOf(true), arrayOf(false))

    @Test(dataProvider = "artTest")
    fun `basic type`(artTest: Boolean) {
        val lv = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(lv, 5))
            instruction(Return.create(null))
        }
        run(block, artTest) { analyzer ->
            val input = analyzer.getNodes(block).last().getSingleInput(lv)
            Assert.assertEquals(input, State.Narrow.forValue(5))
        }
    }

    @Test(dataProvider = "artTest", expectedExceptions = [DexVerifyException::class])
    fun `no arbitrary invokes`(artTest: Boolean) {
        val lv = LocalVariable.narrow()
        val block = block {
            instruction(Const.createString(lv, "abc"))
            instruction(invoke(
                    String::class.java.getMethod("valueOf", CharArray::class.java),
                    lv
            ))
            instruction(Return.createVoid())
        }
        run(block, artTest) { analyzer ->
            analyzer.getNodes(block).last().getSingleInput(lv)
        }
    }

    @Test(dataProvider = "artTest")
    fun `check-cast narrows`(artTest: Boolean) {
        val lv = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNull(lv))
            instruction(CheckCast.create(lv, classpath.getTypeMirror(Type.getType(CharArray::class.java))))
            instruction(invoke(
                    String::class.java.getMethod("valueOf", CharArray::class.java),
                    lv
            ))
            instruction(Return.createVoid())
        }
        run(block, artTest) { analyzer ->
            val input = analyzer.getNodes(block).last().getSingleInput(lv)
            Assert.assertEquals(input, State.OfType(Type.getType(CharArray::class.java)))
        }
    }

    @Test(dataProvider = "artTest")
    fun `narrow value is not narrowed by invoke`(artTest: Boolean) {
        val lv = LocalVariable.narrow()
        val block = block {
            instruction(Const.createNarrow(lv, 4))
            instruction(invoke(String::class.java.getMethod("valueOf", Float::class.java), lv))
            instruction(invoke(String::class.java.getMethod("valueOf", Int::class.java), lv))
            instruction(Return.createVoid())
        }
        run(block, artTest) { analyzer ->
            val input = analyzer.getNodes(block).last().getSingleInput(lv)
            Assert.assertEquals(input, State.Narrow.forValue(4))
        }
    }

    @Test(dataProvider = "artTest", expectedExceptions = [DexVerifyException::class])
    fun `int float merge fails`(artTest: Boolean) {
        val lv = LocalVariable.narrow()
        val block = block {
            val tmp = LocalVariable.narrow()
            instruction(Const.createNarrow(tmp, 5))
            ifBlock(tmp eq Zero) {
                instruction(UnaryOperation.builder().type(UnaryOperation.Type.INT_TO_BYTE).destination(lv).source(tmp)
                        .build())
            } elseBlock {
                instruction(UnaryOperation.builder().type(UnaryOperation.Type.INT_TO_FLOAT).destination(lv).source(tmp)
                        .build())
            }
            instruction(invoke(String::class.java.getMethod("valueOf", Int::class.java), lv))
            instruction(Return.createVoid())
        }
        run(block, artTest)
    }

    @Test(dataProvider = "artTest", expectedExceptions = [DexVerifyException::class])
    fun `return check`(artTest: Boolean) {
        val returnsBool = mainClass.addMethod("returnsBool")
        returnsBool.isStatic = true
        returnsBool.returnType = classpath.getTypeMirror(Type.BOOLEAN_TYPE)
        returnsBool.body = MethodBody(block {
            val tmp = LocalVariable.narrow()
            instruction(Const.createNarrow(tmp, 0xcc))
            ifBlock(tmp eq Zero) {
                instruction(Const.createNarrow(tmp, -1))
            } elseBlock {
                instruction(Const.createNarrow(tmp, 1))
            }
            instruction(Return.create(tmp))
        })
        val clinit = mainClass.addMethod("<clinit>")
        clinit.isStatic = true
        clinit.body = MethodBody(block {
            instruction(Invoke.builder().method(returnsBool).build())
            instruction(Return.createVoid())
        })

        val lv = LocalVariable.narrow()
        val block = block {
            instruction(Invoke.builder().method(returnsBool).build())
            instruction(Return.createVoid())
        }
        run(block, artTest)
    }

    @Test(dataProvider = "artTest")
    fun `and with 1 casts to boolean`(artTest: Boolean) {
        val returnsBool = mainClass.addMethod("returnsBool")
        returnsBool.isStatic = true
        returnsBool.returnType = classpath.getTypeMirror(Type.BOOLEAN_TYPE)
        returnsBool.body = MethodBody(block {
            val tmp = LocalVariable.narrow()
            instruction(Const.createNarrow(tmp, 0xcc))
            ifBlock(tmp eq Zero) {
                instruction(Const.createNarrow(tmp, -1))
            } elseBlock {
                instruction(Const.createNarrow(tmp, 1))
            }
            instruction(LiteralBinaryOperation.builder()
                    .type(LiteralBinaryOperation.Type.AND).destination(tmp).lhs(tmp).rhs(1).build())
            instruction(Return.create(tmp))
        })
        val clinit = mainClass.addMethod("<clinit>")
        clinit.isStatic = true
        clinit.body = MethodBody(block {
            instruction(Invoke.builder().method(returnsBool).build())
            instruction(Return.createVoid())
        })

        val lv = LocalVariable.narrow()
        val block = block {
            instruction(Invoke.builder().method(returnsBool).build())
            instruction(Return.createVoid())
        }
        run(block, artTest)
    }

    @Test(dataProvider = "artTest")
    fun `and with 1 casts to boolean - int type`(artTest: Boolean) {
        val takesBool = mainClass.addMethod("takesBool")
        takesBool.isStatic = true
        takesBool.addParameter(classpath.getTypeMirror(Type.BOOLEAN_TYPE))
        takesBool.body = MethodBody(block {
            instruction(Return.createVoid())
        }).also { it.parameters.add(LocalVariable.narrow()) }

        val block = block {
            val s = LocalVariable.reference()
            val v = LocalVariable.narrow()
            instruction(Const.createString(s, "123"))
            instruction(Invoke.builder()
                    .method(classpath.getTypeMirror(Type.getType(Integer::class.java))
                            .method("parseInt", Type.getMethodType("(Ljava/lang/String;)I"), TriState.TRUE))
                    .parameter(s).returnValue(v)
                    .build())
            instruction(LiteralBinaryOperation.builder()
                    .type(LiteralBinaryOperation.Type.AND)
                    .destination(v).lhs(v).rhs(1)
                    .build())
            instruction(Invoke.builder().method(takesBool).parameter(v).build())
            instruction(Return.createVoid())
        }
        run(block, artTest)
    }

    @Test(dataProvider = "artTest")
    fun `different definition scopes before super constructor call`(artTest: Boolean) {
        val init = mainClass.addMethod("<init>")
        val p0 = LocalVariable.reference()
        val v0 = LocalVariable.reference()
        init.body = MethodBody(block {
            ifBlock(p0 eq Zero) {
                instruction(Const.createString(v0, "abc"))
            }
            instruction(invoke(Any::class.java.getConstructor(), p0, type = Invoke.Type.SPECIAL))
            instruction(Return.createVoid())
        }).also { it.parameters.add(p0) }

        val block = block {
            instruction(Invoke.builder().newInstance().method(init).build())
            instruction(Return.createVoid())
        }
        run(block, artTest)
    }

    @Test(dataProvider = "artTest")
    fun `exception narrowing`(artTest: Boolean) {
        val v0 = LocalVariable.reference()
        val eType = classpath.createClass(Type.getType("LErr;"))
        eType.superType = type("Ljava/lang/Throwable;")
        val block = block {
            tryBlock {
                instruction(Const.createClass(v0, type("LAbsent;")))
            }.catchBlock(eType, v0) {
                // this method is technically inherited, but the verifier doesnt know that.
                instruction(Invoke.builder().method(eType.method(
                        "toString", Type.getMethodType("()Ljava/lang/String;"), TriState.FALSE)).parameter(v0).build())
            }
            instruction(Return.createVoid())
        }
        run(block, artTest)
    }

    @Test(dataProvider = "artTest")
    fun `union type`(artTest: Boolean) {
        val returnsObject = mainClass.addMethod("returnsObject")
        returnsObject.isStatic = true
        returnsObject.returnType = classpath.getTypeMirror(Types.OBJECT)
        returnsObject.body = MethodBody(block {
            val v = LocalVariable.reference()
            instruction(Const.createNull(v))
            instruction(Return.create(v))
        })

        val block = block {
            val s = LocalVariable.reference()
            instruction(Invoke.builder().method(returnsObject).returnValue(s).build())
            val runnable = classpath.getTypeMirror(Type.getType(Runnable::class.java)) as ExternalTypeMirror
            val closeable = classpath.getTypeMirror(Type.getType(Closeable::class.java)) as ExternalTypeMirror
            runnable.isInterface = true
            closeable.isInterface = true
            instruction(CheckCast.create(s, runnable))
            instruction(CheckCast.create(s, closeable))
            instruction(Invoke.builder().method(
                    runnable.method("run", Type.getMethodType("()V"), TriState.FALSE)).parameter(s).build())
            instruction(Invoke.builder().method(
                    runnable.method("close", Type.getMethodType("()V"), TriState.FALSE)).parameter(s).build())
            instruction(Return.createVoid())
        }
        run(block, artTest)
    }

    @Test(timeOut = 1000)
    fun `endless - original`() {
        val r11 = LocalVariable.reference("r11")
        val r12 = LocalVariable.reference("r12")
        val r6n = LocalVariable.narrow("r6n")
        val r6r = LocalVariable.reference("r6r")
        val r1 = LocalVariable.reference("r1")
        val r7n = LocalVariable.narrow("r7n")
        val r7r = LocalVariable.reference("r7r")
        val r0 = LocalVariable.reference("r0")
        val r5 = LocalVariable.reference("r5")
        val r4 = LocalVariable.reference("r4")
        val r8 = LocalVariable.reference("r8")
        val r9 = LocalVariable.narrow("r9")
        val r10 = LocalVariable.reference("r10")
        val r2 = LocalVariable.reference("r2")
        val r3 = LocalVariable.reference("r3")

        val b11 = block {
            instruction(Move.builder().from(r8).to(r10).build())
            instruction(Move.builder().from(r7r).to(r8).build())
            instruction(Move.builder().from(r10).to(r7r).build())
            instruction(invoke(
                    Class.forName("kotlin.io.CloseableKt").getMethod("closeFinally",
                            Closeable::class.java,
                            Throwable::class.java),
                    r6r, r8
            ))
            instruction(Throw.create(r7r))
        }
        b11.exceptionVariable = r8

        val block = block {
            instruction(Const.createNull(r11))
            instruction(CheckCast.create(r11, classpath.getTypeMirror(Type.getType(Reader::class.java))))
            instruction(Const.createNull(r12))
            instruction(CheckCast.create(r12, classpath.getTypeMirror(Type.getType(Function1::class.java))))
            instruction(Const.createString(r6r, "\$receiver"))
            instruction(invoke(
                    Intrinsics::class.java.getMethod("checkParameterIsNotNull", Any::class.java, String::class.java),
                    r11, r6r
            ))
            instruction(Const.createString(r6r, "action"))
            instruction(invoke(
                    Intrinsics::class.java.getMethod("checkParameterIsNotNull", Any::class.java, String::class.java),
                    r12, r6r
            ))
            instruction(Move.builder().from(r11).to(r1).build())
            instruction(Const.createNarrow(r7n, 0x2000))
            instruction(InstanceOf.builder()
                    .target(r6n).operand(r1).type(classpath.getTypeMirror(Type.getType(BufferedReader::class.java)))
                    .build())
            ifBlock(r6n eq Zero) {
                instruction(invoke(
                        BufferedReader::class.java.getConstructor(Reader::class.java, Int::class.java),
                        r1, r7n, ret = r6r
                ))
            } elseBlock {
                instruction(CheckCast.create(r1, classpath.getTypeMirror(Type.getType(BufferedReader::class.java))))
                instruction(Move.builder().from(r1).to(r6r).build())
            }
            instruction(CheckCast.create(r6r, classpath.getTypeMirror(Type.getType(Closeable::class.java))))
            instruction(Const.createNarrow(r7n, 0))
            instruction(Const.createNull(r7r))
            instruction(CheckCast.create(r7r, classpath.getTypeMirror(Type.getType(Throwable::class.java))))

            tryBlock {
                instruction(Move.builder().from(r6r).to(r0).build())
                instruction(CheckCast.create(r0, classpath.getTypeMirror(Type.getType(BufferedReader::class.java))))
                instruction(Move.builder().from(r0).to(r5).build())
                instruction(invoke(
                        Class.forName("kotlin.io.TextStreamsKt").getMethod("lineSequence", BufferedReader::class.java),
                        r5, ret = r4
                ))
                instruction(Const.createNull(r4))
                instruction(CheckCast.create(r4, classpath.getTypeMirror(Type.getType(Sequence::class.java))))
                instruction(Move.builder().from(r12).to(r2).build())
                instruction(invoke(
                        Sequence::class.java.getMethod("iterator"),
                        r4, ret = r8
                ))

                val b5 = label()

                instruction(invoke(
                        Iterator::class.java.getMethod("hasNext"),
                        r8, ret = r9
                ))
                ifBlock(r9 eq Zero) {
                    val unitClass = classpath.getTypeMirror(Type.getType(Unit::class.java))
                    instruction(LoadStore.load()
                            .field(unitClass.field("INSTANCE", unitClass.type, TriState.TRUE))
                            .value(r8)
                            .build())
                } elseBlock {
                    instruction(invoke(
                            Iterator::class.java.getMethod("next"),
                            r8, ret = r3
                    ))
                    instruction(invoke(
                            Function1::class.java.getMethod("invoke", Any::class.java),
                            r2, r8
                    ))
                    goto(b5)
                }
            }.catchBlock(classpath.getTypeMirror(Type.getType(Throwable::class.java)), r7r) {
                label() // random goto
                instruction(Throw.create(r7r))
            }.catchBlock(null, b11)

            instruction(invoke(
                    Class.forName("kotlin.io.CloseableKt").getMethod("closeFinally",
                            Closeable::class.java,
                            Throwable::class.java),
                    r6r, r7r
            ))
            instruction(Return.createVoid())
        }
        run(block, false)
    }

    @Test(timeOut = 1000)
    fun `endless - reduced`() {
        val condition1 = LocalVariable.narrow("condition1")
        val condition2 = LocalVariable.narrow("condition2")
        val bufferedReader = LocalVariable.reference("bufferedReader")
        val reader = LocalVariable.reference("reader")
        val exception = LocalVariable.reference("exception")

        val block = block {
            instruction(Const.createNull(reader))
            instruction(CheckCast.create(reader, classpath.getTypeMirror(Type.getType(Reader::class.java))))
            instruction(Const.createNarrow(condition1, 0))
            ifBlock(condition1 eq Zero) {
                instruction(invoke(
                        BufferedReader::class.java.getConstructor(Reader::class.java),
                        reader, ret = bufferedReader
                ))
            } elseBlock {
                instruction(CheckCast.create(reader, classpath.getTypeMirror(Type.getType(BufferedReader::class.java))))
                instruction(Move.builder().from(reader).to(bufferedReader).build())
            }

            tryBlock {
                val b5 = label()
                instruction(Const.createNarrow(condition2, 0))
                ifBlock(condition2 eq Zero) {
                    goto(b5)
                }
            }.catchBlock(exceptionVariable = exception) {
                instruction(Throw.create(exception))
            }

            instruction(Return.createVoid())
        }
        run(block, false)
    }

    @Test
    fun `super constructor`() {
        val l1 = LocalVariable.reference()
        val l2 = LocalVariable.reference()
        val block = block {
            instruction(Move.builder().from(l1).to(l2).build())
            instruction(Invoke.builder().special()
                    .method(classpath.getTypeMirror(Types.OBJECT).method("<init>",
                            Type.getMethodType("()V"),
                            TriState.FALSE))
                    .parameter(l1)
                    .build())
            instruction(Return.createVoid())
        }
        val a = classpath.createClass(Type.getType("LA;"))
        val body = MethodBody(block)
        body.parameters = listOf(l1)
        val init = a.addMethod("<init>")
        init.body = body
        val verifier = Verifier(classpath, init)
        val analyzer = Analyzer(verifier)
        analyzer.interpret(body)
        Assert.assertEquals(
                analyzer.getNodes(block)[0].input.single(),
                mapOf(l1 to State.UNINITIALIZED_THIS)
        )
        Assert.assertEquals(
                analyzer.getNodes(block)[1].input.single(),
                mapOf(l1 to State.UNINITIALIZED_THIS, l2 to State.UNINITIALIZED_THIS)
        )
        Assert.assertEquals(
                analyzer.getNodes(block)[2].input.single(),
                mapOf(l1 to State.OfType(a.type), l2 to State.OfType(
                        a.type))
        )
    }

    @Test(dataProvider = "artTest", expectedExceptions = [DexVerifyException::class])
    fun `undefined move`(artTest: Boolean) {
        val block = block {
            instruction(Move.builder().from(LocalVariable.narrow()).to(LocalVariable.narrow()).build())
            instruction(Return.createVoid())
        }
        run(block, true)
    }

    @Throws(DexVerifyException::class)
    private fun run(block: BasicBlock, artTest: Boolean, f: (Analyzer<State>) -> Unit = {}) {
        val testMethod = mainClass.addMethod("test")
        testMethod.isStatic = true
        testMethod.body = MethodBody(block)

        if (artTest) {

            val main = mainClass.addMethod("main")
            main.addParameter(classpath.getTypeMirror(Type.getType(Array<String>::class.java)))
            main.isStatic = true
            main.body = MethodBody(block {
                instruction(Return.createVoid())
            })
            main.body!!.parameters = listOf(LocalVariable.reference())

            val compiler = DexCompiler()
            val file = compiler.compile(classpath)

            SmaliUtils.printBaksmali(file, System.out::println)

            val path = Files.createTempFile("VerifierTest", ".dex")
            try {
                DexPool.writeTo(path.toString(), file)

                val result = Art.run(path, "Main")
                println(result.outputString())
                if (result.exitValue == 1 && result.outputString().contains("VerifyError")) {
                    throw DexVerifyException(result.outputString())
                }
                Assert.assertEquals(result.exitValue, 0, result.outputString())
            } finally {
                Files.delete(path)
            }
        } else {
            for (localClass in classpath.localClasses) {
                for (localMethod in localClass.declaredMethods) {
                    val verifier = Verifier(classpath, localMethod)
                    val analyzer = Analyzer<State>(verifier)
                    val methodBody = localMethod.body
                    CodePrinter(methodBody).print("", System.out)
                    analyzer.interpret(methodBody)
                    f(analyzer)
                }
            }
        }
    }

    private fun invoke(method: java.lang.reflect.Executable,
                       vararg vars: LocalVariable,
                       ret: LocalVariable? = null,
                       type: Invoke.Type = if (method is Constructor<*>) Invoke.Type.NEW_INSTANCE else Invoke.Type.NORMAL): Invoke {
        val typeMirror = classpath.getTypeMirror(Type.getType(method.declaringClass))
        (typeMirror as ExternalTypeMirror).isInterface = method.declaringClass.isInterface
        val methodMirror = typeMirror.method(
                if (method is Method) method.name else "<init>",
                if (method is Method) Type.getType(method) else Type.getType(method as Constructor<*>),
                TriState.valueOf(Modifier.isStatic(method.modifiers)))

        return Invoke.builder().type(type).method(methodMirror).parameters(vars.asList()).returnValue(ret).build()
    }

    private fun type(sig: String): TypeMirror {
        return classpath.getTypeMirror(Type.getType(sig))
    }

    private fun method(type: String, name: String, sig: String, static: Boolean): MethodMirror {
        return type(type).method(name, Type.getMethodType(sig), TriState.valueOf(static))
    }

    private fun field(type: String, name: String, sig: String, static: Boolean): FieldMirror {
        return type(type).field(name, Type.getType(sig), TriState.valueOf(static))
    }
}