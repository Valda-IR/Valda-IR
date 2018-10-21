package at.yawk.valda.ir.dex.parser;

import at.yawk.valda.ir.Access;
import at.yawk.valda.ir.Classpath;
import at.yawk.valda.ir.LocalClassMirror;
import at.yawk.valda.ir.LocalFieldMirror;
import at.yawk.valda.ir.LocalMethodMirror;
import at.yawk.valda.ir.MethodMirror;
import at.yawk.valda.ir.MutationGuard;
import at.yawk.valda.ir.NoSuchMemberException;
import at.yawk.valda.ir.TriState;
import at.yawk.valda.ir.TypeMirror;
import at.yawk.valda.ir.annotation.Annotation;
import at.yawk.valda.ir.annotation.AnnotationHolder;
import at.yawk.valda.ir.annotation.AnnotationMember;
import at.yawk.valda.ir.code.BasicBlock;
import at.yawk.valda.ir.code.LocalVariable;
import at.yawk.valda.ir.code.MethodBody;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.AnnotationElement;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.value.AnnotationEncodedValue;
import org.jf.dexlib2.iface.value.ArrayEncodedValue;
import org.jf.dexlib2.iface.value.BooleanEncodedValue;
import org.jf.dexlib2.iface.value.ByteEncodedValue;
import org.jf.dexlib2.iface.value.CharEncodedValue;
import org.jf.dexlib2.iface.value.DoubleEncodedValue;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.iface.value.EnumEncodedValue;
import org.jf.dexlib2.iface.value.FieldEncodedValue;
import org.jf.dexlib2.iface.value.FloatEncodedValue;
import org.jf.dexlib2.iface.value.IntEncodedValue;
import org.jf.dexlib2.iface.value.LongEncodedValue;
import org.jf.dexlib2.iface.value.MethodEncodedValue;
import org.jf.dexlib2.iface.value.NullEncodedValue;
import org.jf.dexlib2.iface.value.ShortEncodedValue;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import org.jf.dexlib2.iface.value.TypeEncodedValue;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@Slf4j
public final class DexParser {
    @Setter @NonNull private DexParserErrorHandler errorHandler = DexParserErrorHandler.getDefault();

    private final List<ClassDef> primary = new ArrayList<>();
    private final List<ClassDef> secondary = new ArrayList<>();

    private Classpath classpath;

    public void add(DexFile dexFile) {
        add(dexFile, false);
    }

    /**
     * @param secondaryDex {@link LocalClassMirror#secondaryDex}
     */
    public void add(DexFile dexFile, boolean secondaryDex) {
        (secondaryDex ? secondary : primary).addAll(dexFile.getClasses());
    }

    public synchronized Classpath parse() {
        classpath = new Classpath();
        for (ClassDef classDef : primary) {
            classpath.createClass(Type.getType(classDef.getType()), null).setSecondaryDex(false);
        }
        for (ClassDef classDef : secondary) {
            classpath.createClass(Type.getType(classDef.getType()), null).setSecondaryDex(true);
        }

        // first pass - type info
        Stream.concat(primary.stream(), secondary.stream()).parallel().forEach(classDef -> MutationGuard.guarded(() -> {
            LocalClassMirror classMirror = (LocalClassMirror) resolveType(classDef.getType());
            int accessFlags = classDef.getAccessFlags();
            classMirror.setAccess(accessFromFlags(accessFlags));
            classMirror.setInterface(AccessFlags.INTERFACE.isSet(accessFlags));
            classMirror.setAnnotation(AccessFlags.ANNOTATION.isSet(accessFlags));
            classMirror.setEnum(AccessFlags.ENUM.isSet(accessFlags));
            classMirror.setSynthetic(AccessFlags.SYNTHETIC.isSet(accessFlags));
            classMirror.setFinal(AccessFlags.FINAL.isSet(accessFlags));
            classMirror.setAbstract(AccessFlags.ABSTRACT.isSet(accessFlags));
            classMirror.setStatic(AccessFlags.STATIC.isSet(accessFlags));
        }));
        // second pass - member definitions
        Stream.concat(primary.stream(), secondary.stream()).parallel().forEach(classDef -> MutationGuard.guarded(() -> {
            LocalClassMirror classMirror = (LocalClassMirror) resolveType(classDef.getType());
            if (classDef.getSuperclass() != null) {
                classMirror.setSuperType(resolveType(classDef.getSuperclass()));
            }
            for (String itf : classDef.getInterfaces()) {
                classMirror.addInterface(resolveType(itf));
            }
            for (Method method : classDef.getMethods()) {
                LocalMethodMirror methodMirror = classMirror.addMethod(method.getName());
                methodMirror.setStatic(AccessFlags.STATIC.isSet(method.getAccessFlags()));
                methodMirror.setAbstract(AccessFlags.ABSTRACT.isSet(method.getAccessFlags()));
                methodMirror.setFinal(AccessFlags.FINAL.isSet(method.getAccessFlags()));
                methodMirror.setSynchronized(AccessFlags.SYNCHRONIZED.isSet(method.getAccessFlags()));
                methodMirror.setBridge(AccessFlags.BRIDGE.isSet(method.getAccessFlags()));
                methodMirror.setVarargs(AccessFlags.VARARGS.isSet(method.getAccessFlags()));
                methodMirror.setNative(AccessFlags.NATIVE.isSet(method.getAccessFlags()));
                methodMirror.setStrictfp(AccessFlags.STRICTFP.isSet(method.getAccessFlags()));
                methodMirror.setSynthetic(AccessFlags.SYNTHETIC.isSet(method.getAccessFlags()));
                methodMirror.setDeclaredSynchronized(AccessFlags.DECLARED_SYNCHRONIZED.isSet(method.getAccessFlags()));
                methodMirror.setAccess(accessFromFlags(method.getAccessFlags()));
                methodMirror.setReturnType(method.getReturnType().equals("V") ?
                                                   null :
                                                   resolveType(method.getReturnType()));
                for (MethodParameter parameter : method.getParameters()) {
                    methodMirror.addParameter(resolveType(parameter.getType()));
                }
            }
            for (Field field : classDef.getFields()) {
                LocalFieldMirror fieldMirror = classMirror.addField(field.getName(), resolveType(field.getType()));
                fieldMirror.setAccess(accessFromFlags(field.getAccessFlags()));
                fieldMirror.setStatic(AccessFlags.STATIC.isSet(field.getAccessFlags()));
                fieldMirror.setFinal(AccessFlags.FINAL.isSet(field.getAccessFlags()));
                fieldMirror.setVolatile(AccessFlags.VOLATILE.isSet(field.getAccessFlags()));
                fieldMirror.setTransient(AccessFlags.TRANSIENT.isSet(field.getAccessFlags()));
                fieldMirror.setSynthetic(AccessFlags.SYNTHETIC.isSet(field.getAccessFlags()));
                fieldMirror.setEnum(AccessFlags.ENUM.isSet(field.getAccessFlags()));
            }
        }));
        // third pass - code, annotations
        // this is not parallel, because it might create members on external types which is not yet thread safe.
        Stream.concat(primary.stream(), secondary.stream()).sequential().forEach(classDef -> {
            LocalClassMirror classMirror = (LocalClassMirror) resolveType(classDef.getType());
            parseAnnotations(classDef.getAnnotations(), classMirror.getAnnotations());
            for (Method method : classDef.getMethods()) {
                LocalMethodMirror methodMirror = classMirror.method(
                        method.getName(), InstructionParser.getMethodType(method),
                        TriState.valueOf(AccessFlags.STATIC.isSet(method.getAccessFlags())));

                parseAnnotations(method.getAnnotations(), methodMirror.getAnnotations());

                for (int i = 0; i < method.getParameters().size(); i++) {
                    parseAnnotations(method.getParameters().get(i).getAnnotations(),
                                     methodMirror.getParameters().get(i).getAnnotations());
                }

                MethodImplementation implementation = method.getImplementation();
                if (implementation != null) {
                    try {
                        methodMirror.setBody(parseCode(method, implementation));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse " + method, e);
                    }
                }
            }
            for (Field field : classDef.getFields()) {
                LocalFieldMirror fieldMirror = classMirror.field(
                        field.getName(),
                        Type.getType(field.getType()),
                        TriState.valueOf(AccessFlags.STATIC.isSet(field.getAccessFlags())));
                parseAnnotations(field.getAnnotations(), fieldMirror.getAnnotations());
                EncodedValue initialValue = field.getInitialValue();
                if (initialValue != null) {
                    fieldMirror.getDefaultValue().set(parseAnnotationMember(initialValue));
                }
            }
        });
        return classpath;
    }

    private void parseAnnotations(
            Set<? extends org.jf.dexlib2.iface.Annotation> annotationsList,
            AnnotationHolder.AnnotationAnnotationHolder target
    ) {
        List<Annotation> annotations = new ArrayList<>();
        for (org.jf.dexlib2.iface.Annotation annotation : annotationsList) {
            annotations.add(parseAnnotation(annotation.getType(), annotation.getElements()));
        }
        if (!annotations.isEmpty()) {
            target.set(new AnnotationMember.Array(annotations));
        }
    }

    private static Access accessFromFlags(int flags) {
        if (AccessFlags.PUBLIC.isSet(flags)) {
            return Access.PUBLIC;
        } else if (AccessFlags.PRIVATE.isSet(flags)) {
            return Access.PRIVATE;
        } else if (AccessFlags.PROTECTED.isSet(flags)) {
            return Access.PROTECTED;
        } else {
            return Access.DEFAULT;
        }
    }

    private MethodBody parseCode(Method method, MethodImplementation implementation) {
        if (log.isTraceEnabled()) {
            log.trace("Parsing {}->{}({}){}",
                      method.getDefiningClass(),
                      method.getName(),
                      String.join("", method.getParameterTypes()),
                      method.getReturnType());
        }

        InstructionList instructions = new InstructionList(implementation.getInstructions());
        TypeChecker typeChecker = new TypeChecker(instructions);

        // put parameters into the last registers
        int paramStart = implementation.getRegisterCount();
        List<LocalVariable> parameters = new ArrayList<>();
        for (MethodParameter parameter : Lists.reverse(method.getParameters())) {
            if (parameter.getType().equals("J") || parameter.getType().equals("D")) {
                paramStart -= 2;
            } else {
                paramStart--;
            }
            parameters.add(0, InstructionParser.registerVariable(
                    paramStart, InstructionParser.asmTypeToVariableType(Type.getType(parameter.getType()))));
        }
        if (!AccessFlags.STATIC.isSet(method.getAccessFlags())) {
            parameters.add(0, InstructionParser.registerVariable(paramStart - 1, LocalVariable.Type.REFERENCE));
            typeChecker.addParameter(paramStart - 1, RegisterType.REFERENCE);
        }
        for (MethodParameter parameter : method.getParameters()) {
            RegisterType type = TypeChecker.asmTypeToRegisterType(Type.getType(parameter.getType()));
            typeChecker.addParameter(paramStart, type);
            if (type == RegisterType.WIDE_PAIR) {
                paramStart += 2;
            } else {
                paramStart++;
            }
        }
        assert paramStart == implementation.getRegisterCount();

        for (TryBlock<? extends ExceptionHandler> tryBlock : implementation.getTryBlocks()) {
            for (ExceptionHandler exceptionHandler : tryBlock.getExceptionHandlers()) {
                typeChecker.addCatch(tryBlock.getStartCodeAddress(),
                                     tryBlock.getStartCodeAddress() + tryBlock.getCodeUnitCount(),
                                     exceptionHandler.getHandlerCodeAddress());
            }
        }

        typeChecker.run();

        InstructionParser instructionParser = new InstructionParser(classpath, instructions, typeChecker);
        instructionParser.errorHandler = errorHandler;
        implementation.getTryBlocks().forEach(instructionParser::addTry);
        BasicBlock entryPoint = instructionParser.run();

        MethodBody body = new MethodBody(entryPoint);
        body.setParameters(parameters);
        return body;
    }

    private TypeMirror resolveType(String type) {
        return classpath.getTypeMirror(Type.getType(type));
    }

    private static final List<Type> ANNOTATION_MEMBER_TYPES_REF = ImmutableList.of(
            Type.getType(String.class),
            Type.getType(Class.class),
            Type.getType(java.lang.reflect.Field.class),
            Type.getType(java.lang.reflect.Method.class),
            Type.getType(Enum.class)
    );
    private static final List<Type> ANNOTATION_MEMBER_TYPES = ImmutableList.<Type>builder()
            .addAll(ANNOTATION_MEMBER_TYPES_REF)
            .add(
                    Type.BOOLEAN_TYPE,
                    Type.BYTE_TYPE,
                    Type.SHORT_TYPE,
                    Type.CHAR_TYPE,
                    Type.INT_TYPE,
                    Type.LONG_TYPE,
                    Type.FLOAT_TYPE,
                    Type.DOUBLE_TYPE
            )
            .build();

    private static List<Type> guessType(AnnotationMember member) {
        if (member instanceof AnnotationMember.Boolean) {
            return Collections.singletonList(Type.BOOLEAN_TYPE);
        } else if (member instanceof AnnotationMember.Byte) {
            return Collections.singletonList(Type.BYTE_TYPE);
        } else if (member instanceof AnnotationMember.Short) {
            return Collections.singletonList(Type.SHORT_TYPE);
        } else if (member instanceof AnnotationMember.Char) {
            return Collections.singletonList(Type.CHAR_TYPE);
        } else if (member instanceof AnnotationMember.Int) {
            return Collections.singletonList(Type.INT_TYPE);
        } else if (member instanceof AnnotationMember.Long) {
            return Collections.singletonList(Type.LONG_TYPE);
        } else if (member instanceof AnnotationMember.Float) {
            return Collections.singletonList(Type.FLOAT_TYPE);
        } else if (member instanceof AnnotationMember.Double) {
            return Collections.singletonList(Type.DOUBLE_TYPE);
        } else if (member instanceof AnnotationMember.String) {
            return Collections.singletonList(Type.getType(String.class));
        } else if (member instanceof AnnotationMember.Type) {
            return Collections.singletonList(Type.getType(Class.class));
        } else if (member instanceof AnnotationMember.Field) {
            return Collections.singletonList(Type.getType(java.lang.reflect.Field.class));
        } else if (member instanceof AnnotationMember.Method) {
            return Collections.singletonList(Type.getType(java.lang.reflect.Method.class));
        } else if (member instanceof AnnotationMember.Enum) {
            return Collections.singletonList(((AnnotationMember.Enum) member).getField().getDeclaringType().getType());
        } else if (member instanceof AnnotationMember.Null) {
            return ANNOTATION_MEMBER_TYPES_REF;
        } else if (member instanceof Annotation) {
            return Collections.singletonList(((Annotation) member).getType().getType());
        } else if (member instanceof AnnotationMember.Array) {
            return ((AnnotationMember.Array) member).getValues().stream()
                    // find the most specific type guess, or use ANNOTATION_MEMBER_TYPES if empty
                    .map(DexParser::guessType).min(Comparator.comparingInt(List::size)).orElse(ANNOTATION_MEMBER_TYPES)
                    // Then map to the respective array type
                    .stream().map(t -> Type.getType("[" + t.getDescriptor())).collect(Collectors.toList());
        } else {
            throw new AssertionError(member.toString());
        }
    }

    @Nullable
    private AnnotationMember parseAnnotationMember(EncodedValue value) {
        if (value instanceof ByteEncodedValue) {
            return new AnnotationMember.Byte(((ByteEncodedValue) value).getValue());
        } else if (value instanceof ShortEncodedValue) {
            return new AnnotationMember.Short(((ShortEncodedValue) value).getValue());
        } else if (value instanceof CharEncodedValue) {
            return new AnnotationMember.Char(((CharEncodedValue) value).getValue());
        } else if (value instanceof IntEncodedValue) {
            return new AnnotationMember.Int(((IntEncodedValue) value).getValue());
        } else if (value instanceof LongEncodedValue) {
            return new AnnotationMember.Long(((LongEncodedValue) value).getValue());
        } else if (value instanceof FloatEncodedValue) {
            return new AnnotationMember.Float(((FloatEncodedValue) value).getValue());
        } else if (value instanceof DoubleEncodedValue) {
            return new AnnotationMember.Double(((DoubleEncodedValue) value).getValue());
        } else if (value instanceof StringEncodedValue) {
            return new AnnotationMember.String(((StringEncodedValue) value).getValue());
        } else if (value instanceof TypeEncodedValue) {
            Type type = Type.getType(((TypeEncodedValue) value).getValue());
            return new AnnotationMember.Type(type.getSort() == Type.VOID ? null : classpath.getTypeMirror(type));
        } else if (value instanceof FieldEncodedValue) {
            try {
                return new AnnotationMember.Field(InstructionParser.resolveField(
                        classpath, ((FieldEncodedValue) value).getValue(), TriState.MAYBE));
            } catch (NoSuchMemberException e) {
                return errorHandler.handleAnnotationValueLinkageError(e);
            }
        } else if (value instanceof MethodEncodedValue) {
            try {
                return new AnnotationMember.Method(InstructionParser.resolveMethod(
                        classpath, ((MethodEncodedValue) value).getValue(), TriState.MAYBE));
            } catch (NoSuchMemberException e) {
                return errorHandler.handleAnnotationValueLinkageError(e);
            }
        } else if (value instanceof EnumEncodedValue) {
            try {
                return new AnnotationMember.Enum(InstructionParser.resolveField(
                        classpath, ((EnumEncodedValue) value).getValue(), TriState.TRUE));
            } catch (NoSuchMemberException e) {
                return errorHandler.handleAnnotationValueLinkageError(e);
            }
        } else if (value instanceof NullEncodedValue) {
            return AnnotationMember.Null.getInstance();
        } else if (value instanceof BooleanEncodedValue) {
            return new AnnotationMember.Boolean(((BooleanEncodedValue) value).getValue());
        } else if (value instanceof AnnotationEncodedValue) {
            return parseAnnotation(((AnnotationEncodedValue) value).getType(),
                                   ((AnnotationEncodedValue) value).getElements());
        } else if (value instanceof ArrayEncodedValue) {
            return new AnnotationMember.Array(((ArrayEncodedValue) value).getValue().stream()
                                                      .map(this::parseAnnotationMember)
                                                      .filter(Objects::nonNull)
                                                      .collect(Collectors.toList()));
        } else {
            throw new UnsupportedOperationException("Unsupported encoded value " + value);
        }
    }

    private Annotation parseAnnotation(String type, Set<? extends AnnotationElement> elements) {
        TypeMirror typeMirror = classpath.getTypeMirror(Type.getType(type));
        ImmutableMap.Builder<MethodMirror, AnnotationMember> builder = ImmutableMap.builder();
        for (AnnotationElement element : elements) {
            AnnotationMember v = parseAnnotationMember(element.getValue());
            if (v != null) {
                MethodMirror key;
                try {
                    key = typeMirror.annotationMethod(element.getName(), guessType(v));
                } catch (NoSuchMemberException e) {
                    key = errorHandler.handleAnnotationKeyLinkageError(e);
                }
                if (key != null) {
                    builder.put(key, v);
                }
            }
        }
        return new Annotation(typeMirror, builder.build());
    }
}
