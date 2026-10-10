package io.github.treetrail.jsonpath;

/**
 * The types of function parameters and results (RFC 9535, section 2.4.1).
 *
 * @see FunctionExtension
 */
public enum FunctionType {
    /** A JSON value, or Nothing; for example a literal or a singular query such as {@code @.price}. */
    VALUE,
    /** True or false; for example a comparison or an existence test. */
    LOGICAL,
    /** The nodes a query selects; for example {@code @.prices[*]} or {@code $..price}. */
    NODES
}
