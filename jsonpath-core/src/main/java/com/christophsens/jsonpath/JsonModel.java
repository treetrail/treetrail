package com.christophsens.jsonpath;

import java.math.BigDecimal;

/**
 * Read-only view of a JSON tree in some object model (plain Java objects, Jackson, Gson, ...).
 *
 * <p>The library never parses JSON itself. Instead, a query runs against an existing tree through
 * this interface, so documents from any JSON library can be queried without conversion.
 *
 * @param <N> the node type of the object model
 */
public interface JsonModel<N> {

    /** Returns the kind of {@code value}. */
    JsonKind kind(N value);

    /** Returns the member names of an object, in the order a query should visit them. */
    Iterable<String> memberNames(N object);

    /** Returns whether an object has a member with the given name. */
    boolean hasMember(N object, String name);

    /** Returns the value of an existing object member. */
    N member(N object, String name);

    /** Returns the number of members of an object. */
    int memberCount(N object);

    /** Returns the number of elements of an array. */
    int size(N array);

    /** Returns the element of an array at a valid index. */
    N element(N array, int index);

    /** Returns the value of a string. */
    String stringValue(N value);

    /** Returns the value of a number. */
    BigDecimal numberValue(N value);

    /** Returns the value of a boolean. */
    boolean booleanValue(N value);
}
