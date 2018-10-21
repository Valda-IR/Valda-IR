package at.yawk.valda.ir.dex.parser;

/**
 * @author yawkat
 */
enum RegisterType {
    NARROW,
    WIDE_PAIR,
    WIDE_LOW,
    WIDE_HIGH,
    REFERENCE,
    REFERENCE_UNINITIALIZED,
    /**
     * {@code const} zero is an oddball, it can be both a {@link #NARROW} or a {@link #REFERENCE} value.
     */
    ZERO,
}
