package at.yawk.valda.ir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import org.objectweb.asm.Type;

/**
 * {@link MethodMirror} for a method that is (possibly) not known on this classpath.
 *
 * @author yawkat
 */
public final class ExternalMethodMirror extends MethodMirror {
    @NonNull @Getter private final String name;
    /**
     * For annotation methods we sometimes can't determine the exact return type.
     *
     * {@literal null} element means void.
     */
    private final List<TypeReference.MethodReturnType> possibleReturnTypes = new ArrayList<>();
    @NonNull @Getter private final List<Parameter> parameters;
    @NonNull TriState isStatic;

    ExternalMethodMirror(
            Classpath classpath,
            TypeMirror declaringClass,
            String name,
            Type type,
            TriState isStatic
    ) {
        this(classpath,
             declaringClass,
             name,
             Arrays.asList(type.getArgumentTypes()),
             Collections.singletonList(type.getReturnType()),
             isStatic);
    }

    ExternalMethodMirror(
            Classpath classpath,
            TypeMirror declaringClass,
            @NonNull String name,
            List<Type> parameterTypes,
            List<Type> returnTypes,
            @NonNull TriState isStatic
    ) {
        super(classpath, declaringClass);
        this.name = name;
        this.isStatic = isStatic;
        for (Type returnType : returnTypes) {
            if (returnType.getSort() == Type.VOID) {
                this.possibleReturnTypes.add(null);
            } else {
                TypeReference.MethodReturnType ref =
                        new TypeReference.MethodReturnType(classpath.getTypeMirror(returnType), this);
                ref.getReferencedType().getReferences().add(ref);
                this.possibleReturnTypes.add(ref);
            }
        }
        this.parameters = parameterTypes.stream()
                .map(p -> new Parameter(classpath.getTypeMirror(p)))
                .collect(Collectors.toList());
    }

    void intersectReturnTypes(List<Type> newTypes, ExternalTypeMirror externalTypeMirror) {
        BitSet pass = new BitSet(possibleReturnTypes.size());
        for (int i = 0; i < possibleReturnTypes.size(); i++) {
            Type type = possibleReturnTypes.get(i) == null ?
                    Type.VOID_TYPE :
                    possibleReturnTypes.get(i).getReferencedType().getType();
            if (newTypes.contains(type)) {
                pass.set(i);
            } else {
                for (Type newType : newTypes) {
                    if (newType.equals(Type.getType(Enum.class)) && isPossiblyEnum(classpath, type)) { pass.set(i); }
                    if (type.equals(Type.getType(Enum.class)) && isPossiblyEnum(classpath, newType)) {
                        // make enum type more specific
                        possibleReturnTypes.get(i).getReferencedType().getReferences()
                                .remove(possibleReturnTypes.get(i));
                        TypeReference.MethodReturnType ref = new TypeReference.MethodReturnType(
                                classpath.getTypeMirror(newType), this);
                        ref.getReferencedType().getReferences().add(ref);
                        possibleReturnTypes.set(i, ref);
                        pass.set(i);
                    }
                }
            }
        }
        if (pass.isEmpty()) {
            throw new NoSuchMemberException(
                    "Return type intersection is empty: Current is " + possibleReturnTypes + ", new is " + newTypes +
                    " on " + getDebugDescriptor(),
                    externalTypeMirror,
                    new MemberSignature(name, Type.getMethodType("()V")),
                    isStatic);
        }
        // remove elements not in the intersection
        int i = 0;
        for (Iterator<TypeReference.MethodReturnType> iterator = possibleReturnTypes.iterator(); iterator.hasNext(); ) {
            TypeReference.MethodReturnType ref = iterator.next();
            if (!pass.get(i++)) {
                iterator.remove();
                if (ref != null) {
                    ref.getReferencedType().getReferences().remove(ref);
                }
            }
        }
    }

    static boolean isPossiblyEnum(Classpath classpath, Type type) {
        if (type.getSort() != Type.OBJECT) { return false; }
        if (Arrays.asList(
                Type.getType(Field.class),
                Type.getType(Method.class),
                Type.getType(Class.class),
                Type.getType(Object.class),
                Type.getType(String.class)
        ).contains(type)) { return false; }
        TypeMirror typeMirror = classpath.getTypeMirror(type);
        if (typeMirror instanceof ExternalTypeMirror) { return true; }
        //noinspection RedundantIfStatement
        if (typeMirror instanceof LocalClassMirror && ((LocalClassMirror) typeMirror).isEnum()) { return true; }
        return false;
    }

    @Override
    public Type getType() {
        Type returnType = possibleReturnTypes.get(0) == null ?
                Type.VOID_TYPE :
                possibleReturnTypes.get(0).getReferencedType().getType();
        for (int i = 1; i < possibleReturnTypes.size(); i++) {
            Type type = possibleReturnTypes.get(i) == null ?
                    Type.VOID_TYPE :
                    possibleReturnTypes.get(i).getReferencedType().getType();
            if (!type.equals(returnType)) {
                if (Types.isReferenceType(returnType) && Types.isReferenceType(type)) {
                    returnType = Type.getType("Ljava/lang/Object;");
                } else {
                    throw new IllegalStateException("Return type is not yet known");
                }
            }
        }
        return Type.getMethodType(returnType, parameters.stream().map(p -> p.getType().getType()).toArray(Type[]::new));
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append("ExternalMethodMirror{ ")
                .append(getDeclaringType().getType()).append("->").append(getName()).append('(');
        for (Parameter parameter : parameters) {
            builder.append(parameter.getType());
        }
        builder.append(')');
        if (possibleReturnTypes.size() > 1) { builder.append('{'); }
        for (TypeReference.MethodReturnType returnType : possibleReturnTypes) {
            builder.append(returnType == null ? Type.VOID_TYPE : returnType.getReferencedType().getType());
        }
        if (possibleReturnTypes.size() > 1) { builder.append('}'); }
        return builder.append(" }").toString();
    }

    @Override
    public String getDebugDescriptor() {
        StringBuilder builder = new StringBuilder()
                .append(getDeclaringType().getType()).append("->").append(getName()).append('(');
        for (Parameter parameter : parameters) {
            builder.append(parameter.getType());
        }
        builder.append(')');
        if (possibleReturnTypes.size() > 1) { builder.append('{'); }
        for (TypeReference.MethodReturnType returnType : possibleReturnTypes) {
            builder.append(returnType == null ? Type.VOID_TYPE : returnType.getReferencedType().getType());
        }
        if (possibleReturnTypes.size() > 1) { builder.append('}'); }
        return builder.toString();
    }

    @Override
    public boolean isStatic() {
        return isStatic.asBoolean(() -> {
            throw new IllegalStateException("Not yet known whether this field is static");
        });
    }
}
