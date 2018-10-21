package at.yawk.valda.ir;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * This class assists in finding members (methods and fields) that a given member <i>overrides</i>.
 *
 * @author yawkat
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class SuperMemberScanner<M extends LocalMember> {
    /*
     * This class is pretty complicated because of the intricacies of package-private member overrides. See the tests
     * for examples.
     */

    private final boolean methods;

    private final Set<LocalClassMirror> visitedTypes = new HashSet<>();
    private final Map<LocalClassMirror, Set<PossibleMember>> possibleMembers = new HashMap<>();
    /**
     * Table of <i>all</i> possible members on a type, including possibly-inherited members.
     */
    private final Table<TypeMirror, PossibleMember, Entry> memberTable = HashBasedTable.create();

    protected abstract Iterable<M> declared(LocalClassMirror classMirror);

    protected abstract void getOrCreate(TypeMirror classMirror, MemberSignature signature, boolean isStatic);

    public static SuperMemberScanner<LocalFieldMirror> fields() {
        return new SuperMemberScanner<LocalFieldMirror>(false) {
            @Override
            protected Iterable<LocalFieldMirror> declared(LocalClassMirror classMirror) {
                return classMirror.getDeclaredFields();
            }

            @Override
            protected void getOrCreate(TypeMirror classMirror, MemberSignature signature, boolean isStatic) {
                classMirror.field(signature.getName(), signature.getType(), TriState.valueOf(isStatic));
            }
        };
    }

    public static SuperMemberScanner<LocalMethodMirror> methods() {
        return new SuperMemberScanner<LocalMethodMirror>(true) {
            @Override
            protected Iterable<LocalMethodMirror> declared(LocalClassMirror classMirror) {
                return classMirror.getDeclaredMethods();
            }

            @Override
            protected void getOrCreate(TypeMirror classMirror, MemberSignature signature, boolean isStatic) {
                classMirror.method(signature, TriState.valueOf(isStatic));
            }
        };
    }

    private void visitType(TypeMirror type) {
        // we visit the whole type at a time (not individual members) so we do not have to scan declared() each time

        if (!(type instanceof LocalClassMirror)) { return; }

        LocalClassMirror lcm = (LocalClassMirror) type;
        if (!visitedTypes.add(lcm)) { return; }

        TypeMirror superType = lcm.getSuperType();
        if (superType != null) {
            visitType(superType);
        }
        for (TypeMirror itf : lcm.getInterfaces()) {
            visitType(itf);
        }
        Set<PossibleMember> possibleMembers = new HashSet<>();
        for (M member : declared(lcm)) {
            visitDeclared(member);
            possibleMembers.add(new PossibleMember(member.getSignature(), member.isStatic()));
        }

        if (superType instanceof LocalClassMirror) {
            possibleMembers.addAll(findAllMembers((LocalClassMirror) superType));
        }
        for (TypeMirror itf : lcm.getInterfaces()) {
            if (itf instanceof LocalClassMirror) {
                for (PossibleMember possibleMember : findAllMembers((LocalClassMirror) itf)) {
                    if (methods && possibleMember.isStatic) { continue; }

                    possibleMembers.add(possibleMember);
                }
            }
        }
        this.possibleMembers.put(lcm, possibleMembers);
    }

    private boolean isInvokeSpecial(MemberSignature signature) {
        return methods && (signature.getName().equals("<init>") ||
                           signature.getName().equals("<clinit>"));
    }

    private void visitDeclared(M member) {
        Access access = member.getAccess();
        MemberSignature signature = member.getSignature();
        PossibleMember id = new PossibleMember(signature, member.isStatic());

        if (isInvokeSpecial(signature)) { return; }

        List<TypeMirror> possibleSuperDefs;
        if (methods) {
            possibleSuperDefs = findPossibleSuperDefs(member.getDeclaringType(), id, access);
        } else {
            possibleSuperDefs = Collections.emptyList();
        }

        memberTable.put(member.getDeclaringType(), id, new Entry(possibleSuperDefs, access, true));
    }

    /**
     * Find the {@link Entry} associated with the given <i>interface method</i>.
     */
    private Entry findOrCreateOnInterface(TypeMirror type, PossibleMember id) {
        Entry present = memberTable.get(type, id);
        if (present == null) {
            if (type instanceof LocalClassMirror) {
                List<TypeMirror> possibleSuperDefs = new ArrayList<>();
                for (TypeMirror itf : ((LocalClassMirror) type).getInterfaces()) {
                    if (findOrCreateOnInterface(itf, id).isPresent()) {
                        possibleSuperDefs.add(itf);
                    }
                }
                // this is always java.lang.Object. support methods like toString on interfaces
                TypeMirror superType = ((LocalClassMirror) type).getSuperType();
                if (findAdHocMember(superType, id).isPresent()) {
                    possibleSuperDefs.add(superType);
                }
                if (possibleSuperDefs.isEmpty()) {
                    present = Entry.DEFINITELY_MISSING;
                } else {
                    present = new Entry(possibleSuperDefs, Access.PUBLIC, false);
                }
            } else {
                present = makeExternalEntry(type, id);
            }
            memberTable.put(type, id, present);
        }
        return present;
    }

    private Entry findAdHocMember(TypeMirror type, PossibleMember id) {
        if (isInvokeSpecial(id.signature)) { return Entry.CONSTRUCTOR; }
        Entry present = memberTable.get(type, id);
        if (present == null) {
            if (type instanceof LocalClassMirror) {
                List<TypeMirror> possibleSuperDefs = findPossibleSuperDefs((LocalClassMirror) type, id, Access.PUBLIC);
                if (possibleSuperDefs.isEmpty()) {
                    present = Entry.DEFINITELY_MISSING;
                } else {
                    present = new Entry(possibleSuperDefs, Access.PUBLIC, false);
                }
            } else {
                present = makeExternalEntry(type, id);
            }
            memberTable.put(type, id, present);
        }
        return present;
    }

    /**
     * Compute all <i>non-interface</i> supertypes that may <i>declare</i> the given method, and all <i>interface</i>
     * supertypes that may <i>have</i> the given method (but not necessarily declare them). Will return at most one
     * declaring type in an inheritance chain.
     */
    private List<TypeMirror> findPossibleSuperDefs(
            LocalClassMirror type, PossibleMember id, Access access
    ) {
        List<TypeMirror> possibleSuperDefs = new ArrayList<>();

        if (access == Access.PUBLIC) {
            findInInterfaces(possibleSuperDefs, type, id);
        }

        boolean lookingForPublic = access == Access.PUBLIC || access == Access.PROTECTED;
        boolean lookingForPackage = access != Access.PRIVATE;

        String pkg = Types.getPackage(type.getType());
        TypeMirror superType = type;
        while (lookingForPublic || lookingForPackage) {
            // for fields and static methods, we only override at most one member.
            if ((!methods || id.isStatic) && !possibleSuperDefs.isEmpty()) {
                break;
            }

            superType = ((LocalClassMirror) superType).getSuperType();
            if (superType == null) { break; }

            Entry superEntry = memberTable.get(superType, id);

            if (superType instanceof LocalClassMirror) {
                if (superEntry != null && !superEntry.declared) { superEntry = null; }

                if (superEntry != null) {
                    boolean samePkg = Types.getPackage(((LocalClassMirror) superType).getName()).equals(pkg);

                    lookingForPublic = false;
                    if (samePkg) { lookingForPackage = false; }

                    if (superEntry.access == Access.PRIVATE) {
                        continue;
                    }

                    if (superEntry.access == Access.DEFAULT && !samePkg) {
                        continue;
                    }

                    possibleSuperDefs.add(superType);
                } else {
                    if (lookingForPublic && access == Access.PUBLIC) {
                        findInInterfaces(possibleSuperDefs, (LocalClassMirror) superType, id);
                    }
                }
            } else {
                if (superEntry == null) {
                    superEntry = makeExternalEntry(superType, id);
                    memberTable.put(superType, id, superEntry);
                }

                if (lookingForPublic && superEntry.isPresent()) {
                    possibleSuperDefs.add(superType);
                }
                break;
            }
        }

        // no duplicate entries
        assert possibleSuperDefs.size() == new HashSet<>(possibleSuperDefs).size() : possibleSuperDefs;

        return possibleSuperDefs;
    }

    private void findInInterfaces(
            List<TypeMirror> possibleSuperDefs,
            LocalClassMirror type,
            PossibleMember id
    ) {
        if (methods) {
            // static methods are not inherited from interfaces
            if (id.isStatic) { return; }
        } else {
            // interfaces don't have non-static fields
            if (!id.isStatic) { return; }
        }

        for (TypeMirror itf : type.getInterfaces()) {
            if (findOrCreateOnInterface(itf, id).isPresent()) {
                if (!possibleSuperDefs.contains(itf)) {
                    possibleSuperDefs.add(itf);
                }
            }
        }
    }

    private Entry makeExternalEntry(TypeMirror type, PossibleMember id) {
        try {
            getOrCreate(type, id.signature, id.isStatic);
            return Entry.TOP_LEVEL_DEF;
        } catch (NoSuchMemberException notFound) {
            return Entry.DEFINITELY_MISSING;
        }
    }

    /**
     * @see #findOverriddenMembers(TypeMirror, PossibleMember)
     */
    public List<TypeMirror> findOverriddenMembers(M member) {
        return findOverriddenMembers(member.getDeclaringType(),
                                     new PossibleMember(member.getSignature(),
                                                        member.isStatic()));
    }

    /**
     * Find all members that the given method may override if it exists, or {@code null} iff the given method
     * definitely does not exist.
     * <p>
     * The returned list contains types that contain a member of the same signature that this member overrides. That
     * member may or may not be {@link LocalMember#isDeclared() declared}. It is guaranteed that repeated calls on
     * the returned types (i.e. recursively calling this method again on each returned type) will eventually yield
     * all types that <i>declare</i> a member that is overridden by the initial member.
     * <p>
     * Consider classes A, B extends A and C extends B. C and A <i>declare</i> a method {@code test()V}. Calling
     * {@code findOverriddenMembers(C, test()V, false)} may yield [B], [A] or [B, A]. Calling {@code
     * findOverriddenMembers(B, test()V, false)} will yield [A]. Calling {@code findOverriddenMembers(A, test()V,
     * false)} will yield [].
     */
    @Nullable
    public List<TypeMirror> findOverriddenMembers(
            TypeMirror declaringType,
            PossibleMember possibleMember
    ) {
        visitType(declaringType);
        return findAdHocMember(declaringType, possibleMember).overriddenMembers;
    }

    /**
     * Find all <i>possible</i> members on this class. This method is not very smart and may return members that do
     * not exist after all (i.e. where {@link #findOverriddenMembers(TypeMirror, PossibleMember)} returns {@code *
     * null}).
     */
    public Collection<PossibleMember> findAllMembers(LocalClassMirror classMirror) {
        visitType(classMirror);
        return possibleMembers.get(classMirror);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Entry {
        static final Entry TOP_LEVEL_DEF = new Entry(Collections.emptyList(), null, true);
        static final Entry CONSTRUCTOR = TOP_LEVEL_DEF;
        static final Entry DEFINITELY_MISSING = new Entry(null, null, false);

        /**
         * A value of {@code null} signifies the members is definitely not present on this type. Any other value
         * signifies that the member <i>may</i> be present. The contained list is the possible supertypes that may
         * also contain this member.
         */
        @Nullable private final List<TypeMirror> overriddenMembers;

        @Nullable private final Access access;
        private final boolean declared;

        boolean isPresent() {
            return overriddenMembers != null;
        }
    }

    @Value
    public static final class PossibleMember {
        private final MemberSignature signature;
        private final boolean isStatic;
    }
}
