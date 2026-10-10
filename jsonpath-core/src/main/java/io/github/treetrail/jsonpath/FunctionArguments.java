package io.github.treetrail.jsonpath;

import java.util.List;

/**
 * The arguments of one call of a function extension, in the order of its parameters. Read each with the
 * accessor of the parameter's declared type; every call has been checked against those types when the query
 * was compiled (RFC 9535, section 2.4.3).
 */
public interface FunctionArguments {

    /** Returns the number of arguments, the number of parameters of the function. */
    int size();

    /**
     * Returns an argument of {@link FunctionType#VALUE}.
     *
     * @throws ClassCastException if the parameter has another type
     */
    FunctionValue value(int index);

    /**
     * Returns an argument of {@link FunctionType#LOGICAL}.
     *
     * @throws ClassCastException if the parameter has another type
     */
    boolean logical(int index);

    /**
     * Returns an argument of {@link FunctionType#NODES}: the values of the selected nodes, in order.
     *
     * @throws ClassCastException if the parameter has another type
     */
    List<FunctionValue> nodes(int index);
}
