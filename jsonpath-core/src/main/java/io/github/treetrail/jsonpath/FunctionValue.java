package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Val;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * A value of {@link FunctionType#VALUE} as a function extension sees it: a JSON value, whatever object model
 * the document uses, or Nothing (RFC 9535, section 2.4.1), the result of a query that selects no node.
 *
 * <p>Function arguments are such values; results are created with {@link #of(String)} and its overloads,
 * {@link #ofNull()} and {@link #nothing()}, or are one of the arguments. Functions cannot create arrays or
 * objects. Values are read-only; do not implement this interface.
 */
public interface FunctionValue {

    /** Nothing: the value of a query that selects no node, also a valid function result. */
    static FunctionValue nothing() {
        return Val.NOTHING;
    }

    /** JSON null. */
    static FunctionValue ofNull() {
        return Val.literal(null);
    }

    /** A JSON string. */
    static FunctionValue of(String value) {
        return Val.literal(Objects.requireNonNull(value, "value"));
    }

    /** A JSON number. */
    static FunctionValue of(BigDecimal value) {
        return Val.literal(Objects.requireNonNull(value, "value"));
    }

    /** A JSON number. */
    static FunctionValue of(long value) {
        return Val.literal(value);
    }

    /** A JSON boolean. */
    static FunctionValue of(boolean value) {
        return Val.literal(value);
    }

    /** Whether this is Nothing; Nothing has no kind and no content. */
    boolean isNothing();

    /**
     * Returns the kind of JSON value.
     *
     * @throws IllegalStateException if this is Nothing
     */
    JsonKind kind();

    /**
     * Returns the string.
     *
     * @throws IllegalStateException if this is not a string
     */
    String string();

    /**
     * Returns the number.
     *
     * @throws IllegalStateException if this is not a number, or not a finite one (NaN or an infinity in a
     *     model that allows them)
     */
    BigDecimal number();

    /**
     * Returns the boolean.
     *
     * @throws IllegalStateException if this is not a boolean
     */
    boolean bool();

    /**
     * Returns the number of elements of an array or members of an object.
     *
     * @throws IllegalStateException if this is neither
     */
    int size();

    /**
     * Returns the element of an array.
     *
     * @throws IllegalStateException if this is not an array
     * @throws IndexOutOfBoundsException if {@code index} is not an index of the array
     */
    FunctionValue element(int index);

    /**
     * Returns the member names of an object.
     *
     * @throws IllegalStateException if this is not an object
     */
    List<String> memberNames();

    /**
     * Returns the member of an object with the given name, or {@link #nothing()} if there is none.
     *
     * @throws IllegalStateException if this is not an object
     */
    FunctionValue member(String name);
}
