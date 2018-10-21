package at.yawk.valda.ir;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

/**
 * @author yawkat
 */
@ThreadSafe
public class References<R> {
    private final Set<R> references = new LinkedHashSet<>();

    private References() {
    }

    @SuppressWarnings("unchecked")
    public static <R> References<R> create(Class<R> base) {
        if (base.equals(TypeReference.class)) {
            return (References<R>) new TypeReferences();
        } else {
            return new References<>();
        }
    }

    public void add(@NonNull R ref) {
        synchronized (references) {
            if (!references.add(ref)) {
                throw new NoSuchElementException("Reference " + ref + " already in reference set");
            }
        }
    }

    public void remove(@NonNull R ref) {
        synchronized (references) {
            if (!references.remove(ref)) {
                throw new NoSuchElementException("Reference " + ref + " not found in reference set");
            }
        }
    }

    public <T extends R> Iterable<@NotNull T> listReferences(Class<T> type) {
        synchronized (references) {
            return ImmutableList.copyOf(Iterables.filter(references, type));
        }
    }

    private static class TypeReferences extends References<TypeReference> {
        private final List<TypeReference.MethodDeclaringType> methods = new ArrayList<>();
        private final List<TypeReference.MethodDeclaringType> methodsUnmodifiable =
                Collections.unmodifiableList(methods);
        private final List<TypeReference.FieldDeclaringType> fields = new ArrayList<>();
        private final List<TypeReference.FieldDeclaringType> fieldsUnmodifiable = Collections.unmodifiableList(fields);

        @Override
        public void add(@NonNull TypeReference ref) {
            super.add(ref);
            if (ref instanceof TypeReference.MethodDeclaringType) {
                methods.add((TypeReference.MethodDeclaringType) ref);
            } else if (ref instanceof TypeReference.FieldDeclaringType) {
                fields.add((TypeReference.FieldDeclaringType) ref);
            }
        }

        @Override
        public void remove(@NonNull TypeReference ref) {
            super.remove(ref);
            if (ref instanceof TypeReference.MethodDeclaringType) {
                methods.remove(ref);
            } else if (ref instanceof TypeReference.FieldDeclaringType) {
                fields.remove(ref);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TypeReference> Iterable<T> listReferences(Class<T> type) {
            if (type.equals(TypeReference.MethodDeclaringType.class)) { return (Iterable<T>) methodsUnmodifiable; }
            if (type.equals(TypeReference.FieldDeclaringType.class)) { return (Iterable<T>) fieldsUnmodifiable; }
            return super.listReferences(type);
        }
    }
}
