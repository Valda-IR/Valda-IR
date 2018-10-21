package at.yawk.valda.ir.printer;

import at.yawk.valda.ir.FieldMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.code.ArrayLength;
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
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import at.yawk.valda.ir.code.Monitor;
import at.yawk.valda.ir.code.Move;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Return;
import at.yawk.valda.ir.code.Switch;
import at.yawk.valda.ir.code.TerminatingInstruction;
import at.yawk.valda.ir.code.Throw;
import at.yawk.valda.ir.code.Try;
import at.yawk.valda.ir.code.UnaryOperation;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;

/**
 * @author yawkat
 */
@RequiredArgsConstructor
public final class CodePrinter {
    private final MethodBody body;

    @Setter private boolean showIdentity = false;

    private boolean built = false;

    private final List<BasicBlock> blocks = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final List<Reference> references = new ArrayList<>();
    private ObjectIntMap<BasicBlock> blockStarts;

    private int indent = 0;

    private void visitBlockForIndex(BasicBlock block) {
        if (blocks.contains(block)) { return; }
        blocks.add(block);

        TerminatingInstruction terminatingInstruction = block.getTerminatingInstruction();
        for (BasicBlock successor : terminatingInstruction.getSuccessors()) {
            visitBlockForIndex(successor);
        }
        if (block.getTry() != null) {
            for (Try.Catch handler : block.getTry().getHandlers()) {
                visitBlockForIndex(handler.getHandler());
            }
        }
    }

    private void printLine(String line) {
        for (int i = 0; i < indent; i++) {
            //noinspection StringConcatenationInLoop
            line = " " + line;
        }
        lines.add(line);
    }

    private void printLine(String line, Object... parameters) {
        StringBuilder out = new StringBuilder();
        Iterator<String> parts = Splitter.on("%s").split(line).iterator();
        for (Object parameter : parameters) {
            out.append(parts.next());
            appendParameter(out, parameter);
        }
        out.append(parts.next());
        if (parts.hasNext()) {
            throw new IllegalArgumentException("Format string underflow");
        }
        printLine(out.toString());
    }

    private void appendParameter(StringBuilder out, Object parameter) {
        if (parameter instanceof LocalVariable) {
            out.append(getVariableName((LocalVariable) parameter));
        } else if (parameter instanceof BasicBlock) {
            out.append(reference((BasicBlock) parameter));
        } else if (parameter instanceof String) {
            out.append(parameter);
        } else if (parameter instanceof Integer) {
            out.append("0x").append(Integer.toHexString((Integer) parameter));
        } else if (parameter instanceof Short) {
            out.append("0x").append(Integer.toHexString(((Short) parameter) & 0xffff));
        } else if (parameter instanceof Long) {
            out.append("0x").append(Long.toHexString((Long) parameter)).append("L");
        } else if (parameter instanceof Enum<?>) {
            out.append(((Enum) parameter).name().toLowerCase().replace("_", "-"));
        } else if (parameter instanceof Iterable<?>) {
            out.append("[");
            boolean first = true;
            for (Object o : (Iterable) parameter) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                appendParameter(out, o);
            }
            out.append("]");
        } else if (parameter instanceof TypeMirror) {
            out.append(type((TypeMirror) parameter));
        } else if (parameter instanceof MethodMirror) {
            out.append(method((MethodMirror) parameter));
        } else if (parameter instanceof FieldMirror) {
            out.append(field((FieldMirror) parameter));
        } else {
            throw new UnsupportedOperationException("Parameter " + parameter);
        }
    }

    private String reference(BasicBlock block) {
        boolean found = false;
        for (Reference reference : references) {
            if (reference.to.equals(block)) {
                reference.from.add(lines.size());
                found = true;
                break;
            }
        }
        if (!found) {
            Reference reference = new Reference(block);
            reference.from.add(lines.size());
            references.add(reference);
        }
        return getBlockName(block);
    }

    private void indent(Runnable r) {
        indent++;
        try {
            r.run();
        } finally {
            indent--;
        }
    }

    public void print(String indent, Appendable out) throws IOException {
        if (!built) {
            visitBlockForIndex(body.getEntryPoint());
            MutableObjectIntMap<BasicBlock> blockStarts = ObjectIntMaps.mutable.empty();
            for (BasicBlock block : blocks) {
                blockStarts.put(block, lines.size());
                printBlock(block);
            }
            this.blockStarts = blockStarts;
            references.sort(Comparator
                                    .comparingInt(Reference::firstLine)
                                    .thenComparingInt(r -> ~r.lastLine())
                                    .reversed());
            List<Reference> openRefs = new ArrayList<>();
            int levels = 0;
            for (int i = 0; i < lines.size(); i++) {
                for (Reference ref : references) {
                    if (ref.firstLine() == i) {
                        ref.level = -1;
                        boolean conflict;
                        do {
                            ref.level++;
                            conflict = false;
                            for (Reference openRef : openRefs) {
                                if (openRef.level == ref.level) {
                                    conflict = true;
                                    break;
                                }
                            }
                        } while (conflict);
                        openRefs.add(ref);
                        levels = Math.max(ref.level + 1, levels);
                    }
                    if (ref.lastLine() == i - 1) {
                        openRefs.remove(ref);
                    }
                }
            }
            openRefs.clear();
            for (int i = 0; i < lines.size(); i++) {
                for (Reference reference : references) {
                    if (reference.firstLine() == i) {
                        openRefs.add(reference);
                    }
                    if (reference.lastLine() == i - 1) {
                        openRefs.remove(reference);
                    }
                }
                int[] refString = new int[levels + 1];
                int fillUntil = levels + 1;
                for (Reference ref : openRefs) {
                    int j = levels - ref.level - 1;
                    if (refString[j] != 0) { throw new AssertionError(openRefs); }
                    if (ref.getLines().contains(i)) {
                        if (ref.firstLine() == i) {
                            refString[j] = '┌';
                        } else if (ref.lastLine() == i) {
                            refString[j] = '└';
                        } else {
                            refString[j] = '├';
                        }
                        fillUntil = Math.min(fillUntil, j);
                        refString[levels] = ref.from.contains(i) ? '─' : '→';
                    } else {
                        refString[j] = '│';
                    }
                }
                for (int j = 0; j < refString.length; j++) {
                    if (refString[j] == 0) {
                        refString[j] = j < fillUntil ? ' ' : '─';
                    }
                }
                lines.set(i, new String(refString, 0, refString.length) + lines.get(i));
            }

            built = true;
        }

        for (String line : lines) {
            out.append(indent).append(line).append('\n');
        }
    }

    private void printBlock(BasicBlock block) {
        if (showIdentity) {
            printLine("%s (%s):", getBlockName(block), String.format("%08x", System.identityHashCode(block)));
        } else {
            printLine("%s:", getBlockName(block));
        }
        indent(() -> {
            if (block.equals(body.getEntryPoint()) && !body.getParameters().isEmpty()) {
                printLine("Parameters:");
                indent(() -> {
                    for (LocalVariable variable : body.getParameters()) {
                        printLine("Parameter " + getVariableName(variable));
                    }
                });
            }
            if (block.getExceptionVariable() != null) {
                printLine("Exception variable: " + getVariableName(block.getExceptionVariable()));
            }
            for (Instruction instruction : block.getInstructions()) {
                printInstruction(instruction);
            }
            if (block.getTry() != null) {
                printLine("Exceptions: ");
                indent(() -> {
                    for (Try.Catch handler : block.getTry().getHandlers()) {
                        TypeMirror exceptionType = handler.getExceptionType();
                        printLine("%s: %s", exceptionType == null ? "any" : type(exceptionType), handler.getHandler());
                    }
                });
            }
        });
    }

    private void printInstruction(Instruction instruction) {
        if (showIdentity) {
            printLine("insn#%s", String.format("%08x", System.identityHashCode(instruction)));
            indent++;
        }
        if (instruction instanceof ArrayLength) {
            printLine("array-length %s <- %s",
                      ((ArrayLength) instruction).getTarget(),
                      ((ArrayLength) instruction).getOperand());
        } else if (instruction instanceof ArrayLoadStore) {
            ArrayLoadStore loadStore = (ArrayLoadStore) instruction;
            if (loadStore.getType() == LoadStore.Type.STORE) {
                printLine("array-store-%s %s[%s] <- %s",
                          loadStore.getElementType(),
                          loadStore.getArray(), loadStore.getIndex(), loadStore.getValue());
            } else {
                printLine("array-load-%s %s <- %s[%s]",
                          loadStore.getElementType(),
                          loadStore.getValue(),
                          loadStore.getArray(),
                          loadStore.getIndex());
            }
        } else if (instruction instanceof BinaryOperation) {
            BinaryOperation binary = (BinaryOperation) instruction;
            printLine("%s %s <- %s, %s", binary.getType(), binary.getDestination(), binary.getLhs(), binary.getRhs());
        } else if (instruction instanceof Branch) {
            Branch branch = (Branch) instruction;
            if (branch.getRhs() == null) {
                printLine("%s %s, 0 ? %s : %s",
                          branch.getType(),
                          branch.getLhs(),
                          branch.getBranchTrue(),
                          branch.getBranchFalse());
            } else {
                printLine("%s %s, %s ? %s : %s",
                          branch.getType(),
                          branch.getLhs(),
                          branch.getRhs(),
                          branch.getBranchTrue(),
                          branch.getBranchFalse());
            }
        } else if (instruction instanceof CheckCast) {
            printLine("check-cast %s %s", ((CheckCast) instruction).getVariable(), ((CheckCast) instruction).getType());
        } else if (instruction instanceof Const) {
            Const.Value value = ((Const) instruction).getValue();
            Object valueObject;
            if (value instanceof Const.String) {
                String unescaped = ((Const.String) value).getValue();
                StringBuilder escaped = new StringBuilder();
                for (int i = 0; i < unescaped.length(); i++) {
                    char c = unescaped.charAt(i);
                    if (c == '\\') {
                        escaped.append("\\\\");
                    } else if (c >= 0x30 && c < 0x7f) {
                        escaped.append(c);
                    } else {
                        escaped.append("\\u").append(String.format("%04x", (int) c));
                    }
                }
                valueObject = escaped.toString();
            } else if (value instanceof Const.Null) {
                valueObject = "null";
            } else if (value instanceof Const.Narrow) {
                valueObject = ((Const.Narrow) value).getValue();
            } else if (value instanceof Const.Wide) {
                valueObject = ((Const.Wide) value).getValue();
            } else if (value instanceof Const.Class) {
                valueObject = ((Const.Class) value).getValue();
            } else {
                throw new AssertionError();
            }
            printLine("const %s <- %s", ((Const) instruction).getTarget(), valueObject);
        } else if (instruction instanceof FillArray) {
            printLine("fill-array %s <- %s",
                      ((FillArray) instruction).getArray(),
                      ((FillArray) instruction).getContents().toString());
        } else if (instruction instanceof GoTo) {
            printLine("goto %s", ((GoTo) instruction).getTarget());
        } else if (instruction instanceof InstanceOf) {
            InstanceOf instanceOf = (InstanceOf) instruction;
            printLine("instance-of %s <- %s, %s",
                      instanceOf.getTarget(),
                      instanceOf.getOperand(),
                      instanceOf.getType());
        } else if (instruction instanceof Invoke) {
            Invoke invoke = (Invoke) instruction;
            String name;
            switch (invoke.getType()) {
                case NORMAL: {
                    name = "invoke";
                    break;
                }
                case SPECIAL: {
                    name = "invoke-special";
                    break;
                }
                case NEW_INSTANCE: {
                    name = "new-instance";
                    break;
                }
                default:
                    throw new AssertionError();
            }
            if (invoke.getMethod().isStatic()) {
                name = "invoke-static";
            }
            if (invoke.getReturnValue() == null) {
                printLine("%s %s, %s", name, invoke.getParameters(), invoke.getMethod());
            } else {
                printLine("%s %s <- %s, %s", name, invoke.getReturnValue(), invoke.getParameters(), invoke.getMethod());
            }
        } else if (instruction instanceof LiteralBinaryOperation) {
            LiteralBinaryOperation operation = (LiteralBinaryOperation) instruction;
            printLine("%s %s <- %s, %s",
                      operation.getType(),
                      operation.getDestination(),
                      operation.getLhs(),
                      operation.getRhs());
        } else if (instruction instanceof LoadStore) {
            LoadStore loadStore = (LoadStore) instruction;
            if (loadStore.getType() == LoadStore.Type.STORE) {
                if (loadStore.getInstance() == null) {
                    printLine("store %s <- %s", loadStore.getField(), loadStore.getValue());
                } else {
                    printLine("store %s %s <- %s", loadStore.getInstance(), loadStore.getField(), loadStore.getValue());
                }
            } else {
                if (loadStore.getInstance() == null) {
                    printLine("load %s <- %s", loadStore.getValue(), loadStore.getField());
                } else {
                    printLine("load %s <- %s %s", loadStore.getValue(), loadStore.getInstance(), loadStore.getField());
                }
            }
        } else if (instruction instanceof Monitor) {
            printLine("monitor-%s %s", ((Monitor) instruction).getType(), ((Monitor) instruction).getMonitor());
        } else if (instruction instanceof Move) {
            printLine("move %s <- %s", ((Move) instruction).getTo(), ((Move) instruction).getFrom());
        } else if (instruction instanceof NewArray) {
            NewArray newArray = (NewArray) instruction;
            if (newArray.hasVariables()) {
                printLine("new-array %s <- %s %s", newArray.getTarget(), newArray.getType(), newArray.getVariables());
            } else {
                printLine("new-array %s <- %s %s", newArray.getTarget(), newArray.getType(), newArray.getLength());
            }
        } else if (instruction instanceof Return) {
            if (((Return) instruction).getReturnValue() == null) {
                printLine("return");
            } else {
                printLine("return %s", ((Return) instruction).getReturnValue());
            }
        } else if (instruction instanceof Switch) {
            printLine("switch %s", ((Switch) instruction).getOperand());
            indent(() -> {
                ((Switch) instruction).getBranches().forEachKeyValue((k, v) -> printLine("%s -> %s", k, v));
                printLine("default -> %s", ((Switch) instruction).getDefaultBranch());
            });
        } else if (instruction instanceof Throw) {
            printLine("throw %s", ((Throw) instruction).getException());
        } else if (instruction instanceof UnaryOperation) {
            printLine("%s %s <- %s",
                      ((UnaryOperation) instruction).getType(),
                      ((UnaryOperation) instruction).getDestination(),
                      ((UnaryOperation) instruction).getSource());
        } else {
            printLine(instruction.toString());
        }
        if (showIdentity) {
            indent--;
        }
    }

    private String getVariableName(LocalVariable variable) {
        return variable.getName();
    }

    private String type(TypeMirror typeMirror) {
        return typeMirror.getType().getDescriptor();
    }

    private String method(MethodMirror methodMirror) {
        return methodMirror.getDebugDescriptor();
    }

    private String field(FieldMirror fieldMirror) {
        return type(fieldMirror.getDeclaringType()) + "->" + fieldMirror.getName() + ":" +
               type(fieldMirror.getType());
    }

    private String getBlockName(BasicBlock block) {
        int i = blocks.indexOf(block);
        if (i == -1) { throw new NoSuchElementException(); }
        return "Block #" + (i + 1);
    }

    @ToString
    @RequiredArgsConstructor
    private class Reference {
        final MutableIntSet from = IntSets.mutable.empty();
        final BasicBlock to;

        @Getter(lazy = true) private final IntSet lines = computeLines();

        int level;

        int toLine() {
            return blockStarts.getOrThrow(to);
        }

        IntSet computeLines() {
            MutableIntSet lines = IntSets.mutable.ofAll(from);
            lines.add(toLine());
            return lines;
        }

        int firstLine() {
            return getLines().min();
        }

        int lastLine() {
            return getLines().max();
        }
    }
}
