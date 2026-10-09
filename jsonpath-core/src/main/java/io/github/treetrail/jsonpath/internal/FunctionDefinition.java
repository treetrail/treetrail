package io.github.treetrail.jsonpath.internal;

import java.util.List;
import java.util.function.Function;

/**
 * A function extension (RFC 9535, section 2.4) with its declared parameter and result types.
 *
 * <p>Arguments are passed as {@link Val} (ValueType), {@link Boolean} (LogicalType) or
 * {@code List<Val>} (NodesType, one value per node); the body returns a value of the declared result type.
 */
public record FunctionDefinition(String name, List<Type> parameters, Type result, Function<List<Object>, Object> body) {

    /** The RFC 9535 function expression types. */
    public enum Type {
        VALUE,
        LOGICAL,
        NODES
    }
}
