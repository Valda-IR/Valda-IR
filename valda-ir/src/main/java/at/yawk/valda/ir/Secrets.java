package at.yawk.valda.ir;

import at.yawk.valda.ir.annotation.AnnotationHolder;
import at.yawk.valda.ir.annotation.AnnotationPath;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.InstanceOf;
import at.yawk.valda.ir.code.Invoke;
import at.yawk.valda.ir.code.LoadStore;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.SecretsHolder;
import at.yawk.valda.ir.code.Try;

/**
 * @author yawkat
 */
public final class Secrets {
    static final Secrets SECRETS = new Secrets();

    static {
        SecretsHolder.setSecrets(SECRETS);
        at.yawk.valda.ir.annotation.SecretsHolder.setSecrets(SECRETS);
    }

    private Secrets() {
    }

    public static void init() {
    }

    public TypeReference.InstanceOfType instanceOfTypeReference(TypeMirror referencedType, InstanceOf instruction) {
        return new TypeReference.InstanceOfType(referencedType, instruction);
    }

    public TypeReference.NewArrayType newArrayTypeReference(TypeMirror referencedType, NewArray instruction) {
        return new TypeReference.NewArrayType(referencedType, instruction);
    }

    public TypeReference.CatchExceptionType newCatchExceptionType(TypeMirror referencedType, Try.Catch catch_) {
        return new TypeReference.CatchExceptionType(referencedType, catch_);
    }

    public TypeReference.ConstClass newConst(TypeMirror referencedType, Const const_) {
        return new TypeReference.ConstClass(referencedType, const_);
    }

    public TypeReference.Cast newCast(TypeMirror referencedType, CheckCast checkCast) {
        return new TypeReference.Cast(referencedType, checkCast);
    }

    public FieldReference.LoadStore newLoadStoreReference(FieldMirror referencedField, LoadStore instruction) {
        return new FieldReference.LoadStore(referencedField, instruction);
    }

    public MethodReference.Invoke newInvoke(MethodMirror referencedMethod, Invoke instruction) {
        return new MethodReference.Invoke(referencedMethod, instruction);
    }

    public MethodReference.AnnotationKey methodAnnotationKey(
            MethodMirror referencedMethod, AnnotationHolder<?> holder, AnnotationPath path
    ) {
        return new MethodReference.AnnotationKey(referencedMethod, holder, path);
    }

    public MethodReference.AnnotationMember methodAnnotationMember(
            MethodMirror referencedMethod, AnnotationHolder<?> holder, AnnotationPath path
    ) {
        return new MethodReference.AnnotationMember(referencedMethod, holder, path);
    }

    public FieldReference.AnnotationMember fieldAnnotationMember(
            FieldMirror referencedField, AnnotationHolder<?> holder, AnnotationPath path
    ) {
        return new FieldReference.AnnotationMember(referencedField, holder, path);
    }

    public TypeReference.AnnotationType annotationType(
            TypeMirror referencedType, AnnotationHolder<?> holder, AnnotationPath path
    ) {
        return new TypeReference.AnnotationType(referencedType, holder, path);
    }

    public TypeReference.AnnotationMember typeAnnotationMember(
            TypeMirror referencedType, AnnotationHolder<?> holder, AnnotationPath path
    ) {
        return new TypeReference.AnnotationMember(referencedType, holder, path);
    }
}
