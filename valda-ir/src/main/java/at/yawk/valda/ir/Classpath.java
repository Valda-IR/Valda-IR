package at.yawk.valda.ir;

import com.google.common.collect.Iterables;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import org.objectweb.asm.Type;

/**
 * @author yawkat
 */
@ThreadSafe
public final class Classpath {
    private final Map<Type, TypeMirror> types = new ConcurrentHashMap<>();

    public Classpath() {
    }

    public boolean hasLocalClass(Type type) {
        // null-safe
        return types.get(type) instanceof LocalClassMirror;
    }

    public boolean hasType(Type type) {
        return types.containsKey(type);
    }

    public TypeMirror getTypeMirror(Type type) {
        TypeMirror computedPass1 = types.computeIfAbsent(type, t -> {
            if (!Types.isVariableType(t)) {
                throw new IllegalArgumentException("Not a variable type: " + t);
            }
            if (Types.isArrayType(t)) {
                return null;
            }
            return new ExternalTypeMirror(this, t);
        });
        if (computedPass1 != null) { return computedPass1; }
        TypeMirror componentType = getTypeMirror(Types.getComponentType(type));
        return types.computeIfAbsent(type, t -> new ArrayTypeMirror(this, componentType));
    }

    void updateType(TypeMirror mirror, Type oldType, Type newType) {
        if (types.putIfAbsent(newType, mirror) != null) {
            throw new IllegalArgumentException("Type already present: " + newType);
        }
        if (!types.remove(oldType, mirror)) {
            types.remove(newType, mirror);
            throw new IllegalArgumentException("Could not remove old type");
        }
    }

    public LocalClassMirror createClass(Type type) {
        return createClass(type, getTypeMirror(Type.getType(Object.class)));
    }

    public LocalClassMirror createClass(Type type, @Nullable TypeMirror superType) {
        if (!Types.isSimpleClassType(type)) {
            throw new IllegalArgumentException("Type " + type + " is not a class type");
        }
        LocalClassMirror mirror = new LocalClassMirror(this, type.getClassName(), superType);
        TypeMirror old = types.putIfAbsent(type, mirror);
        if (old != null) {
            throw new IllegalStateException("Type " + type + " already exists");
        }
        return mirror;
    }

    public Iterable<LocalClassMirror> getLocalClasses() {
        return Iterables.filter(types.values(), LocalClassMirror.class);
    }
}
