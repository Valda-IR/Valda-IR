package at.yawk.valda.ir;

/**
 * @author yawkat
 */
public interface LocalMember extends Member {
    Access getAccess();

    void setName(String name);

    /**
     * "Undeclared" members do not exist in the actual dex file but are invocation or field-load/store targets.
     *
     * Assume there are two classes:
     *
     * <pre>
     * class A {
     *     static void run() {}
     * }
     * class B extends A {}
     * </pre>
     *
     * An invocation {@code B.run()} will produce an {@code invoke-static {}, LB;->run()V}. It is effectively
     * calling a non-existent method {@code run()V} on the class {@code B} that at runtime actually calls the method
     * on {@code A}. However, if <i>after</i> compilation a method {@code run()V} is added to {@code B}, that method
     * is called instead. Similar rules apply to fields.
     *
     * To handle this behavior we introduce the concept of "declared members". Undeclared members can be
     * "materialized" to real members if necessary, while maintaining the expected dispatch rules.
     */
    boolean isDeclared();

    @Override
    LocalClassMirror getDeclaringType();
}
