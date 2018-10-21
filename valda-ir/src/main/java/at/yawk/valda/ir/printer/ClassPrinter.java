package at.yawk.valda.ir.printer;

import at.yawk.valda.ir.Access;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.LocalClassMirror;
import at.yawk.valda.ir.LocalFieldMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.TypeMirror;
import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * @author yawkat
 */
@UtilityClass
public final class ClassPrinter {
    public static void printClasspath(Appendable appendable, Classpath classpath) throws IOException {
        for (LocalClassMirror classMirror : classpath.getLocalClasses()) {
            printClass(appendable, classMirror);
            appendable.append('\n');
        }
    }

    public static void printClass(Appendable appendable, LocalClassMirror classMirror) throws IOException {
        appendAccess(appendable, classMirror.getAccess());
        if (classMirror.isStatic()) { appendable.append("static "); }
        if (classMirror.isFinal()) { appendable.append("final "); }
        if (classMirror.isAbstract()) { appendable.append("abstract "); }
        if (classMirror.isSynthetic()) { appendable.append("synthetic "); }

        if (classMirror.isAnnotation()) {
            appendable.append("@interface ");
        } else if (classMirror.isInterface()) {
            appendable.append("interface ");
        } else if (classMirror.isEnum()) {
            appendable.append("enum ");
        } else {
            appendable.append("class ");
        }

        appendable.append(classMirror.getName()).append('\n');

        if (classMirror.getSuperType() != null) {
            appendable
                    .append(" extends ")
                    .append(classMirror.getSuperType().getType().getDescriptor())
                    .append('\n');
        }
        for (TypeMirror itf : classMirror.getInterfaces()) {
            appendable
                    .append(" implements ")
                    .append(itf.getType().getDescriptor())
                    .append('\n');
        }

        List<LocalFieldMirror> fields = classMirror.getDeclaredFields();
        if (!fields.isEmpty()) {
            appendable.append('\n');
            for (LocalFieldMirror field : fields) {
                printField(" ", appendable, field);
            }
        }
        for (LocalMethodMirror method : classMirror.getDeclaredMethods()) {
            appendable.append('\n');
            printMethod(" ", appendable, method);
        }
    }

    private static void appendAccess(Appendable appendable, Access access) throws IOException {
        switch (access) {
            case PRIVATE: {
                appendable.append("private ");
                break;
            }
            case DEFAULT:
                break;
            case PROTECTED: {
                appendable.append("protected ");
                break;
            }
            case PUBLIC: {
                appendable.append("public ");
                break;
            }
        }
    }

    public static void printField(String indent, Appendable appendable, LocalFieldMirror fieldMirror)
            throws IOException {
        appendable.append(indent);
        appendAccess(appendable, fieldMirror.getAccess());
        if (fieldMirror.isStatic()) { appendable.append("static "); }
        if (fieldMirror.isFinal()) { appendable.append("final "); }
        if (fieldMirror.isVolatile()) { appendable.append("volatile "); }
        if (fieldMirror.isTransient()) { appendable.append("transient "); }
        if (fieldMirror.isSynthetic()) { appendable.append("synthetic "); }
        if (fieldMirror.isEnum()) { appendable.append("enum "); }
        appendable.append(fieldMirror.getName())
                .append(':')
                .append(fieldMirror.getType().getType().getDescriptor())
                .append('\n');
    }

    public static void printMethod(String indent, Appendable appendable, LocalMethodMirror methodMirror)
            throws IOException {
        appendable.append(indent);
        appendAccess(appendable, methodMirror.getAccess());
        if (methodMirror.isStatic()) { appendable.append("static "); }
        if (methodMirror.isFinal()) { appendable.append("final "); }
        if (methodMirror.isSynchronized()) { appendable.append("synchronized "); }
        if (methodMirror.isBridge()) { appendable.append("bridge "); }
        if (methodMirror.isVarargs()) { appendable.append("varargs "); }
        if (methodMirror.isNative()) { appendable.append("native "); }
        if (methodMirror.isAbstract()) { appendable.append("abstract "); }
        if (methodMirror.isStrictfp()) { appendable.append("strictfp "); }
        if (methodMirror.isSynthetic()) { appendable.append("synthetic "); }
        if (methodMirror.isDeclaredSynchronized()) { appendable.append("declared-synchronized "); }
        appendable.append(methodMirror.getName()).append(methodMirror.getType().getDescriptor()).append('\n');

        if (methodMirror.getBody() != null) {
            new CodePrinter(methodMirror.getBody()).print(indent + " ", appendable);
        }
    }
}
