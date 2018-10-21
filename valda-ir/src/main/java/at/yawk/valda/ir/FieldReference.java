package at.yawk.valda.ir;

import at.yawk.valda.ir.annotation.AnnotationHolder;
import at.yawk.valda.ir.annotation.AnnotationPath;
import lombok.Getter;

/**
 * @author yawkat
 */
@Getter
public abstract class FieldReference {
    private final FieldMirror referencedField;

    FieldReference(FieldMirror referencedField) {
        this.referencedField = referencedField;
    }

    @Getter
    public static abstract class Instruction<I extends at.yawk.valda.ir.code.Instruction> extends FieldReference {
        private final I instruction;

        Instruction(FieldMirror referencedField, I instruction) {
            super(referencedField);
            this.instruction = instruction;
        }
    }

    public static final class LoadStore extends Instruction<at.yawk.valda.ir.code.LoadStore> {
        LoadStore(FieldMirror referencedField, at.yawk.valda.ir.code.LoadStore instruction) {
            super(referencedField, instruction);
        }
    }

    @Getter
    public static final class AnnotationMember extends FieldReference {
        private final AnnotationHolder<?> holder;
        private final AnnotationPath path;

        AnnotationMember(FieldMirror referencedField, AnnotationHolder<?> holder, AnnotationPath path) {
            super(referencedField);
            this.holder = holder;
            this.path = path;
        }
    }
}
