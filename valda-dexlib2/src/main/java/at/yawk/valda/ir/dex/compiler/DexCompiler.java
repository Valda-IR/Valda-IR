package at.yawk.valda.ir.dex.compiler;

import at.yawk.valda.ir.Access;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.LocalClassMirror;
import at.yawk.valda.ir.LocalFieldMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.annotation.Annotation;
import at.yawk.valda.ir.annotation.AnnotationHolder;
import at.yawk.valda.ir.annotation.AnnotationMember;
import at.yawk.valda.ir.code.MethodBody;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.AnnotationVisibility;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.immutable.ImmutableAnnotation;
import org.jf.dexlib2.immutable.ImmutableAnnotationElement;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.dexlib2.immutable.ImmutableField;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.ImmutableMethodParameter;
import org.jf.dexlib2.immutable.value.ImmutableAnnotationEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableArrayEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableBooleanEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableByteEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableCharEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableDoubleEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableEnumEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableFieldEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableFloatEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableIntEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableLongEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableMethodEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableNullEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableShortEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableStringEncodedValue;
import org.jf.dexlib2.immutable.value.ImmutableTypeEncodedValue;

/**
 * @author yawkat
 */
@Slf4j
public final class DexCompiler {
    private final Opcodes opcodes = Opcodes.getDefault();

    public DexFile compile(Classpath classpath) {
        return new ImmutableDexFile(
                opcodes,
                Streams.stream(classpath.getLocalClasses())
                        .map(this::compileClass)
                        .collect(ImmutableList.toImmutableList())
        );
    }

    public ImmutableClassDef compileClass(LocalClassMirror classMirror) {
        int accessFlags = 0;
        accessFlags |= accessToFlags(classMirror.getAccess());
        if (classMirror.isAbstract()) { accessFlags |= AccessFlags.ABSTRACT.getValue(); }
        if (classMirror.isAnnotation()) { accessFlags |= AccessFlags.ANNOTATION.getValue(); }
        if (classMirror.isEnum()) { accessFlags |= AccessFlags.ENUM.getValue(); }
        if (classMirror.isFinal()) { accessFlags |= AccessFlags.FINAL.getValue(); }
        if (classMirror.isInterface()) { accessFlags |= AccessFlags.INTERFACE.getValue(); }
        if (classMirror.isStatic()) { accessFlags |= AccessFlags.STATIC.getValue(); }
        if (classMirror.isSynthetic()) { accessFlags |= AccessFlags.SYNTHETIC.getValue(); }

        ImmutableSortedSet.Builder<ImmutableField> staticFields = ImmutableSortedSet.naturalOrder();
        ImmutableSortedSet.Builder<ImmutableField> instanceFields = ImmutableSortedSet.naturalOrder();

        for (LocalFieldMirror fieldMirror : classMirror.getDeclaredFields()) {
            ImmutableField compiledField = compileField(fieldMirror);
            if (fieldMirror.isStatic()) {
                staticFields.add(compiledField);
            } else {
                instanceFields.add(compiledField);
            }
        }

        ImmutableSortedSet.Builder<ImmutableMethod> directMethods = ImmutableSortedSet.naturalOrder();
        ImmutableSortedSet.Builder<ImmutableMethod> virtualMethods = ImmutableSortedSet.naturalOrder();

        for (LocalMethodMirror methodMirror : classMirror.getDeclaredMethods()) {
            ImmutableMethod compiledMethod = compileMethod(methodMirror);
            if (methodMirror.isStatic() || methodMirror.getAccess() == Access.PRIVATE || methodMirror.isConstructor()) {
                directMethods.add(compiledMethod);
            } else {
                virtualMethods.add(compiledMethod);
            }
        }

        return new ImmutableClassDef(
                classMirror.getType().getDescriptor(),
                accessFlags,
                classMirror.getSuperType() == null ? null : classMirror.getSuperType().getType().getDescriptor(),
                classMirror.getInterfaces().stream()
                        .map(t -> t.getType().getDescriptor())
                        .collect(ImmutableList.toImmutableList()),
                null,
                compileAnnotations(classMirror.getAnnotations()),
                staticFields.build(), instanceFields.build(),
                directMethods.build(), virtualMethods.build()
        );
    }

    private static int accessToFlags(Access access) {
        switch (access) {
            case PRIVATE:
                return AccessFlags.PRIVATE.getValue();
            case DEFAULT:
                return 0;
            case PROTECTED:
                return AccessFlags.PROTECTED.getValue();
            case PUBLIC:
                return AccessFlags.PUBLIC.getValue();
            default:
                throw new AssertionError();
        }
    }

    private ImmutableField compileField(LocalFieldMirror fieldMirror) {
        int accessFlags = accessToFlags(fieldMirror.getAccess());
        if (fieldMirror.isStatic()) { accessFlags |= AccessFlags.STATIC.getValue(); }
        if (fieldMirror.isFinal()) { accessFlags |= AccessFlags.FINAL.getValue(); }
        if (fieldMirror.isVolatile()) { accessFlags |= AccessFlags.VOLATILE.getValue(); }
        if (fieldMirror.isTransient()) { accessFlags |= AccessFlags.TRANSIENT.getValue(); }
        if (fieldMirror.isSynthetic()) { accessFlags |= AccessFlags.SYNTHETIC.getValue(); }
        if (fieldMirror.isEnum()) { accessFlags |= AccessFlags.ENUM.getValue(); }
        AnnotationMember defaultValue = fieldMirror.getDefaultValue().get();
        return new ImmutableField(
                fieldMirror.getDeclaringType().getType().getDescriptor(),
                fieldMirror.getName(),
                fieldMirror.getType().getType().getDescriptor(),
                accessFlags,
                defaultValue == null ? null : compileEncodedValue(defaultValue),
                compileAnnotations(fieldMirror.getAnnotations())
        );
    }

    private ImmutableMethod compileMethod(LocalMethodMirror methodMirror) {
        int access = accessToFlags(methodMirror.getAccess());
        if (methodMirror.isStatic()) { access |= AccessFlags.STATIC.getValue(); }
        if (methodMirror.isAbstract()) { access |= AccessFlags.ABSTRACT.getValue(); }
        if (methodMirror.isFinal()) { access |= AccessFlags.FINAL.getValue(); }
        if (methodMirror.isSynchronized()) { access |= AccessFlags.SYNCHRONIZED.getValue(); }
        if (methodMirror.isBridge()) { access |= AccessFlags.BRIDGE.getValue(); }
        if (methodMirror.isVarargs()) { access |= AccessFlags.VARARGS.getValue(); }
        if (methodMirror.isNative()) { access |= AccessFlags.NATIVE.getValue(); }
        if (methodMirror.isStrictfp()) { access |= AccessFlags.STRICTFP.getValue(); }
        if (methodMirror.isSynthetic()) { access |= AccessFlags.SYNTHETIC.getValue(); }
        if (methodMirror.isDeclaredSynchronized()) { access |= AccessFlags.DECLARED_SYNCHRONIZED.getValue(); }
        if (methodMirror.isConstructor() || methodMirror.isStaticInitializer()) {
            access |= AccessFlags.CONSTRUCTOR.getValue();
        }

        ImmutableMethodImplementation implementation;
        MethodBody body = methodMirror.getBody();
        if (body != null) {
            if (log.isTraceEnabled()) {
                log.trace("Compiling {}", methodMirror.getDebugDescriptor());
            }
            NaiveCodeCompiler codeCompiler = new NaiveCodeCompiler(body.getParameters());
            codeCompiler.add(body.getEntryPoint());
            implementation = codeCompiler.compile();
        } else {
            implementation = null;
        }

        return new ImmutableMethod(
                methodMirror.getDeclaringType().getType().getDescriptor(),
                methodMirror.getName(),
                methodMirror.getParameters().stream()
                        .map(p -> new ImmutableMethodParameter(
                                p.getType().getType().getDescriptor(),
                                compileAnnotations(p.getAnnotations()),
                                null
                        ))
                        .collect(ImmutableList.toImmutableList()),
                methodMirror.getReturnType() == null ? "V" : methodMirror.getReturnType().getType().getDescriptor(),
                access,
                compileAnnotations(methodMirror.getAnnotations()),
                implementation
        );
    }

    private ImmutableSet<ImmutableAnnotation> compileAnnotations(AnnotationHolder.AnnotationAnnotationHolder holder) {
        return holder.getAnnotations().stream()
                .map(a -> {
                    String descriptor = a.getType().getType().getDescriptor();
                    int visibility = descriptor.startsWith("Ldalvik/annotation/") ?
                            AnnotationVisibility.SYSTEM :
                            AnnotationVisibility.RUNTIME;
                    return new ImmutableAnnotation(
                            visibility,
                            descriptor,
                            a.getValues().entrySet().stream()
                                    .map(e -> new ImmutableAnnotationElement(e.getKey().getName(),
                                                                             compileEncodedValue(e.getValue())))
                                    .collect(ImmutableSet.toImmutableSet())
                    );
                })
                .collect(ImmutableSet.toImmutableSet());
    }

    private ImmutableEncodedValue compileEncodedValue(AnnotationMember member) {
        if (member instanceof AnnotationMember.Byte) {
            return new ImmutableByteEncodedValue(((AnnotationMember.Byte) member).getValue());
        } else if (member instanceof AnnotationMember.Short) {
            return new ImmutableShortEncodedValue(((AnnotationMember.Short) member).getValue());
        } else if (member instanceof AnnotationMember.Char) {
            return new ImmutableCharEncodedValue(((AnnotationMember.Char) member).getValue());
        } else if (member instanceof AnnotationMember.Int) {
            return new ImmutableIntEncodedValue(((AnnotationMember.Int) member).getValue());
        } else if (member instanceof AnnotationMember.Long) {
            return new ImmutableLongEncodedValue(((AnnotationMember.Long) member).getValue());
        } else if (member instanceof AnnotationMember.Float) {
            return new ImmutableFloatEncodedValue(((AnnotationMember.Float) member).getValue());
        } else if (member instanceof AnnotationMember.Double) {
            return new ImmutableDoubleEncodedValue(((AnnotationMember.Double) member).getValue());
        } else if (member instanceof AnnotationMember.Boolean) {
            return ImmutableBooleanEncodedValue.forBoolean(((AnnotationMember.Boolean) member).isValue());
        } else if (member instanceof AnnotationMember.String) {
            return new ImmutableStringEncodedValue(((AnnotationMember.String) member).getValue());
        } else if (member instanceof AnnotationMember.Type) {
            TypeMirror typeMirror = ((AnnotationMember.Type) member).getType();
            return new ImmutableTypeEncodedValue(
                    typeMirror == null ? "V" : typeMirror.getType().getDescriptor());
        } else if (member instanceof AnnotationMember.Method) {
            return new ImmutableMethodEncodedValue(References.method(((AnnotationMember.Method) member).getMethod()));
        } else if (member instanceof AnnotationMember.Field) {
            return new ImmutableFieldEncodedValue(References.field(((AnnotationMember.Field) member).getField()));
        } else if (member instanceof AnnotationMember.Enum) {
            return new ImmutableEnumEncodedValue(References.field(((AnnotationMember.Enum) member).getField()));
        } else if (member instanceof AnnotationMember.Array) {
            return new ImmutableArrayEncodedValue(
                    ((AnnotationMember.Array) member).getValues().stream()
                            .map(this::compileEncodedValue)
                            .collect(ImmutableList.toImmutableList())
            );
        } else if (member instanceof Annotation) {
            return new ImmutableAnnotationEncodedValue(
                    ((Annotation) member).getType().getType().getDescriptor(),
                    ((Annotation) member).getValues().entrySet().stream()
                            .map(e -> new ImmutableAnnotationElement(e.getKey().getName(),
                                                                     compileEncodedValue(e.getValue())))
                            .collect(ImmutableSet.toImmutableSet())
            );
        } else if (member instanceof AnnotationMember.Null) {
            return ImmutableNullEncodedValue.INSTANCE;
        } else {
            throw new AssertionError(member.toString());
        }
    }
}
