package at.yawk.valda.ir;

import at.yawk.valda.ir.annotation.AnnotationHolder;
import at.yawk.valda.ir.annotation.AnnotationPath;
import at.yawk.valda.ir.code.CheckCast;
import at.yawk.valda.ir.code.Const;
import at.yawk.valda.ir.code.InstanceOf;
import at.yawk.valda.ir.code.NewArray;
import at.yawk.valda.ir.code.Try;
import lombok.Getter;
import lombok.ToString;

/**
 * @author yawkat
 */
@Getter
public abstract class TypeReference {
    private final TypeMirror referencedType;

    TypeReference(TypeMirror referencedType) {
        this.referencedType = referencedType;
    }

    @ToString
    @Getter
    public static abstract class SuperType extends TypeReference {
        private final LocalClassMirror declaringType;

        SuperType(TypeMirror referencedType, LocalClassMirror declaringType) {
            super(referencedType);
            this.declaringType = declaringType;
        }
    }

    public static final class Extends extends SuperType {
        Extends(TypeMirror referencedType, LocalClassMirror declaringType) {
            super(referencedType, declaringType);
        }
    }

    public static final class Implements extends SuperType {
        Implements(TypeMirror referencedType, LocalClassMirror declaringType) {
            super(referencedType, declaringType);
        }
    }

    @ToString
    @Getter
    public static final class ArrayComponentType extends TypeReference {
        private final ArrayTypeMirror arrayType;

        ArrayComponentType(TypeMirror referencedType, ArrayTypeMirror arrayType) {
            super(referencedType);
            this.arrayType = arrayType;
        }
    }

    @ToString
    @Getter
    public static final class MethodDeclaringType extends TypeReference {
        private final MethodMirror method;

        MethodDeclaringType(TypeMirror referencedType, MethodMirror method) {
            super(referencedType);
            this.method = method;
        }
    }

    @ToString
    @Getter
    public static final class MethodReturnType extends TypeReference {
        private final MethodMirror method;

        MethodReturnType(TypeMirror referencedType, MethodMirror method) {
            super(referencedType);
            this.method = method;
        }
    }

    @ToString
    @Getter
    public static final class FieldDeclaringType extends TypeReference {
        private final FieldMirror field;

        FieldDeclaringType(TypeMirror referencedType, FieldMirror field) {
            super(referencedType);
            this.field = field;
        }
    }

    @ToString
    @Getter
    public static final class FieldType extends TypeReference {
        private final FieldMirror field;

        FieldType(TypeMirror referencedType, FieldMirror field) {
            super(referencedType);
            this.field = field;
        }
    }

    @ToString
    @Getter
    public static final class ParameterType extends TypeReference {
        private final MethodMirror.Parameter parameter;

        ParameterType(TypeMirror referencedType, MethodMirror.Parameter parameter) {
            super(referencedType);
            this.parameter = parameter;
        }
    }

    @Getter
    public static abstract class Instruction<I extends at.yawk.valda.ir.code.Instruction> extends TypeReference {
        private final I instruction;

        Instruction(TypeMirror referencedType, I instruction) {
            super(referencedType);
            this.instruction = instruction;
        }
    }

    public static final class InstanceOfType extends Instruction<InstanceOf> {
        InstanceOfType(TypeMirror referencedType, InstanceOf instruction) {
            super(referencedType, instruction);
        }
    }

    public static final class NewArrayType extends Instruction<NewArray> {
        NewArrayType(TypeMirror referencedType, NewArray instruction) {
            super(referencedType, instruction);
        }
    }

    public static final class ConstClass extends Instruction<Const> {
        ConstClass(TypeMirror referencedType, Const instruction) {
            super(referencedType, instruction);
        }
    }

    public static final class Cast extends Instruction<CheckCast> {
        Cast(TypeMirror referencedType, CheckCast instruction) {
            super(referencedType, instruction);
        }
    }

    public static final class CatchExceptionType extends TypeReference {
        private final Try.Catch catch_;

        CatchExceptionType(TypeMirror referencedType, Try.Catch catch_) {
            super(referencedType);
            this.catch_ = catch_;
        }

        public Try.Catch getCatch() {
            return catch_;
        }
    }

    @Getter
    public static final class AnnotationMember extends TypeReference {
        private final AnnotationHolder<?> holder;
        private final AnnotationPath path;

        AnnotationMember(TypeMirror referencedType, AnnotationHolder<?> holder, AnnotationPath path) {
            super(referencedType);
            this.holder = holder;
            this.path = path;
        }
    }

    @Getter
    public static final class AnnotationType extends TypeReference {
        private final AnnotationHolder<?> holder;
        private final AnnotationPath path;

        AnnotationType(TypeMirror referencedType, AnnotationHolder<?> holder, AnnotationPath path) {
            super(referencedType);
            this.holder = holder;
            this.path = path;
        }
    }
}
