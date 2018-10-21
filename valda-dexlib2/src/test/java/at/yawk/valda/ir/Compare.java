package at.yawk.valda.ir;

import at.yawk.valda.ir.code.ArrayLoadStore;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.BinaryOperation;
import at.yawk.valda.ir.code.Branch;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.FillArray;
import at.yawk.valda.ir.code.GoTo;
import at.yawk.valda.ir.code.InstanceOf;
import at.yawk.valda.ir.code.Instruction;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LiteralBinaryOperation;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.Move;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Switch;
import at.yawk.valda.ir.code.UnaryOperation;
import at.yawk.valda.ir.dex.parser.DexParser;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

/**
 * Compares two dex files to detect translation issues
 *
 * @author yawkat
 */
@Slf4j
public final class Compare {
    public static void main(String[] args) throws IOException {
        Classpath in = load(Paths.get("test/in-apk/classes.dex"));
        Classpath out = load(Paths.get("test/out.dex"));
        compareGeneric(in.getLocalClasses(), out.getLocalClasses(),
                       "Class", LocalClassMirror::getType, Compare::compare);
    }

    private static <M, K> void compareGeneric(
            Iterable<M> a,
            Iterable<M> b,
            String name,
            Function<M, K> signature,
            BiConsumer<M, M> compare
    ) {
        Map<K, M> aMembers = Streams.stream(a).collect(Collectors.toMap(signature, m -> m));
        Map<K, M> bMembers = Streams.stream(b).collect(Collectors.toMap(signature, m -> m));
        for (K notInB : Sets.difference(aMembers.keySet(), bMembers.keySet())) {
            log.info(name + " {} is not present in B", notInB);
        }
        for (K notInA : Sets.difference(bMembers.keySet(), aMembers.keySet())) {
            log.info(name + " {} is not present in A", notInA);
        }
        for (Map.Entry<K, M> aEntry : aMembers.entrySet()) {
            M aMember = aEntry.getValue();
            M bMember = bMembers.get(aEntry.getKey());
            if (bMember == null) { continue; }
            compare.accept(aMember, bMember);
        }
    }

    private static Classpath load(Path path) throws IOException {
        DexBackedDexFile dexFile = new DexBackedDexFile(Opcodes.getDefault(), Files.readAllBytes(path));
        DexParser parser = new DexParser();
        parser.add(dexFile);
        return parser.parse();
    }

    private static void compare(LocalClassMirror a, LocalClassMirror b) {
        compareGeneric(a.getDeclaredMethods(), b.getDeclaredMethods(),
                       "Method", m -> new MemberSignature(m.getName(), m.getType()), Compare::compare);
        compareGeneric(a.getDeclaredFields(), b.getDeclaredFields(),
                       "Field", m -> new MemberSignature(m.getName(), m.getType().getType()), (l, r) -> {
                });
    }

    private static void compare(LocalMethodMirror a, LocalMethodMirror b) {
        if (a.getBody() == null) {
            if (b.getBody() != null) {
                log.info("Method A {} has no body", a.getDebugDescriptor());
            }
            return;
        } else if (b.getBody() == null) {
            log.info("Method B {} has no body", b.getDebugDescriptor());
            return;
        }

        compare(a.getDebugDescriptor(), a.getBody(), b.getBody());
    }

    private static void compare(String ctx, MethodBody a, MethodBody b) {
        Queue<Pair<BasicBlock>> blockQueue = new ArrayDeque<>();
        blockQueue.add(new Pair<>(flatten(a.getEntryPoint()), flatten(b.getEntryPoint())));
        Set<Pair<BasicBlock>> visited = new HashSet<>();

        while (true) {
            Pair<BasicBlock> next = blockQueue.poll();
            if (next == null) { break; }
            if (!visited.add(next)) { continue; }
            compare(ctx, next.getA(), next.getB());
            List<BasicBlock> nextA = next.getA().getTerminatingInstruction().getSuccessors();
            List<BasicBlock> nextB = next.getB().getTerminatingInstruction().getSuccessors();
            if (nextA.size() != nextB.size()) {
                log.info("{}: Incompatible successors for blocks. Aborting this path.", ctx);
            } else {
                for (int i = 0; i < nextA.size(); i++) {
                    blockQueue.add(new Pair<>(flatten(nextA.get(i)), flatten(nextB.get(i))));
                }
            }

            if (next.getA().getTry() != null) {
                if (next.getB().getTry() == null) {
                    log.info("{}: Missing try in B", ctx);
                } else {
                    compareGeneric(
                            next.getA().getTry().getHandlers(),
                            next.getB().getTry().getHandlers(),
                            "Try",
                            h -> h.getExceptionType() == null ? "*" : h.getExceptionType().getType().getDescriptor(),
                            (l, r) -> blockQueue.add(new Pair<>(flatten(l.getHandler()), flatten(r.getHandler())))
                    );
                }
            } else if (next.getB().getTry() != null) {
                log.info("{}: Missing try in A", ctx);
            }
        }
    }

    private static BasicBlock flatten(BasicBlock block) {
        while (block.getInstructions().size() == 1 && block.getTerminatingInstruction() instanceof GoTo) {
            block = ((GoTo) block.getTerminatingInstruction()).getTarget();
        }
        return block;
    }

    private static void compare(String ctx, BasicBlock a, BasicBlock b) {
        Queue<Instruction> qA = new ArrayDeque<>(a.getInstructions());
        Queue<Instruction> qB = new ArrayDeque<>(b.getInstructions());
        while (true) {
            Instruction iA = qA.peek();
            if (iA != null && canSkip(iA)) {
                qA.poll();
                continue;
            }
            Instruction iB = qB.peek();
            if (iB != null && canSkip(iB)) {
                qB.poll();
                continue;
            }
            if (iA == null) {
                if (iB != null) {
                    log.info("{}: Too many instructions in B {}", ctx, qB);
                }
                break;
            } else if (iB == null) {
                log.info("{}: Too many instructions in A {}", ctx, qA);
                break;
            }
            qA.poll();
            qB.poll();

            if (!iA.getClass().equals(iB.getClass())) {
                log.info("{}: Mismatched instruction types {} :: {}", ctx, iA, iB);
                continue;
            }

            if (iA instanceof ArrayLoadStore) {
                if (((ArrayLoadStore) iA).getType() != ((ArrayLoadStore) iB).getType()) {
                    log.info("{}: Incompatible array load/stores {} :: {}", ctx, iA, iB);
                } else if (((ArrayLoadStore) iA).getElementType() != ((ArrayLoadStore) iB).getElementType()) {
                    log.info("{}: Incompatible array load/store element types {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof BinaryOperation) {
                if (((BinaryOperation) iA).getType() != ((BinaryOperation) iB).getType()) {
                    log.info("{}: Incompatible binary ops {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof Branch) {
                if (((Branch) iA).getType() != ((Branch) iB).getType()) {
                    log.info("{}: Incompatible branch types {} :: {}", ctx, iA, iB);
                } else if ((((Branch) iA).getRhs() == null) != (((Branch) iB).getRhs() == null)) {
                    log.info("{}: Incompatible branch RHS {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof CheckCast) {
                if (!equals(((CheckCast) iA).getType(), ((CheckCast) iB).getType())) {
                    log.info("{}: Incompatible cast types {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof FillArray) {
                if (!((FillArray) iA).getContents().equals(((FillArray) iB).getContents())) {
                    log.info("{}: Incompatible fill-array contents {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof InstanceOf) {
                if (!equals(((InstanceOf) iA).getType(), ((InstanceOf) iB).getType())) {
                    log.info("{}: Incompatible instance-of types {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof Invoke) {
                if (((Invoke) iA).getType() != ((Invoke) iB).getType()) {
                    log.info("{}: Incompatible invoke types {} :: {}", ctx, iA, iB);
                } else if (!equals(((Invoke) iA).getMethod(), ((Invoke) iB).getMethod())) {
                    log.info("{}: Incompatible invoke targets {} :: {}", ctx, iA, iB);
                } else if ((((Invoke) iA).getReturnValue() == null) != (((Invoke) iB).getReturnValue() == null)) {
                    log.info("{}: Incompatible invoke return values {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof LiteralBinaryOperation) {
                if (((LiteralBinaryOperation) iA).getType() != ((LiteralBinaryOperation) iB).getType()) {
                    log.info("{}: Incompatible literal binary ops {} :: {}", ctx, iA, iB);
                } else if (((LiteralBinaryOperation) iA).getRhs() != ((LiteralBinaryOperation) iB).getRhs()) {
                    log.info("{}: Incompatible literal binary values {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof LoadStore) {
                if (((LoadStore) iA).getType() != ((LoadStore) iB).getType()) {
                    log.info("{}: Incompatible load/stores {} :: {}", ctx, iA, iB);
                } else if (!equals(((LoadStore) iA).getField(), ((LoadStore) iB).getField())) {
                    log.info("{}: Incompatible load/store fields {} :: {}", ctx, iA, iB);
                } else if ((((LoadStore) iA).getInstance() == null) != (((LoadStore) iB).getInstance() == null)) {
                    log.info("{}: Incompatible load/store instances {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof NewArray) {
                if (!equals(((NewArray) iA).getType(), ((NewArray) iB).getType())) {
                    log.info("{}: Incompatible new-array types {} :: {}", ctx, iA, iB);
                } else if (((NewArray) iA).hasVariables() != ((NewArray) iB).hasVariables()) {
                    log.info("{}: Incompatible new-array values {} :: {}", ctx, iA, iB);
                } else if (((NewArray) iA).hasVariables() &&
                           ((NewArray) iA).getVariables().size() != ((NewArray) iB).getVariables().size()) {
                    log.info("{}: Incompatible new-array values {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof Switch) {
                if (!((Switch) iA).getBranches().keySet().equals(((Switch) iB).getBranches().keySet())) {
                    log.info("{}: Incompatible switch branches {} :: {}", ctx, iA, iB);
                }
            } else if (iA instanceof UnaryOperation) {
                if (((UnaryOperation) iA).getType() != ((UnaryOperation) iB).getType()) {
                    log.info("{}: Incompatible unary ops {} :: {}", ctx, iA, iB);
                }
            }
        }
    }

    private static boolean equals(TypeMirror a, TypeMirror b) {
        return a.getType().equals(b.getType());
    }

    private static boolean equals(MethodMirror a, MethodMirror b) {
        return a.getName().equals(b.getName()) && a.getType().equals(b.getType()) &&
               equals(a.getDeclaringType(), b.getDeclaringType());
    }

    private static boolean equals(FieldMirror a, FieldMirror b) {
        return a.getName().equals(b.getName()) && equals(a.getType(), b.getType()) &&
               equals(a.getDeclaringType(), b.getDeclaringType());
    }

    private static boolean canSkip(Instruction i) {
        return i instanceof Move || i instanceof Const;
    }

    @Value
    private static class Pair<T> {
        private final T a;
        private final T b;
    }
}
