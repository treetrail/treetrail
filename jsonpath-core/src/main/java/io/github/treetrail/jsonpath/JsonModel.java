package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Values;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Read-only view of a JSON tree in some object model (plain Java objects, Jackson, Gson, ...).
 *
 * <p>The library never parses JSON itself. Instead, a query runs against an existing tree through
 * this interface, so documents from any JSON library can be queried without conversion.
 *
 * <h2>Implementing a model</h2>
 *
 * <p>The abstract methods are all a model needs. The default methods ({@link #members},
 * {@link #findMember}, {@link #isLong} and {@link #longValue}) are built on them; override them when the
 * object model can answer faster, for example by iterating its entries once instead of looking up each
 * member by name. Implementations must be safe for use by several threads at once; a stateless
 * singleton is typical.
 *
 * <p>The query engine only calls a method with a value of the kind the method is documented for, for
 * example {@link #size} only with arrays.
 *
 * <h2>Compatibility</h2>
 *
 * <p>Methods added to this interface in later versions will be default methods, so implementations
 * keep compiling and working. The JSONPath Compliance Test Suite can be run against a model to check it.
 *
 * @param <N> the node type of the object model
 */
public interface JsonModel<N extends @Nullable Object> {

    /**
     * Returns whether two values are equal as JSON values, with the semantics of {@code ==} in filters: numbers
     * by value, strings by content, arrays element by element and objects member by member regardless of
     * order. The values may come from different models, for example a Jackson tree and plain Java objects:
     *
     * <pre>{@code
     * JsonModel.jsonEquals(Jackson2Model.INSTANCE, node, JavaObjectModel.INSTANCE, Map.of("price", 399));
     * }</pre>
     *
     * @throws JsonPathLimitExceededException if the values are nested more than 1,000 levels deep
     * @throws IllegalArgumentException if a value is not a JSON value of its model
     */
    @SuppressWarnings("unchecked")
    static <A extends @Nullable Object, B extends @Nullable Object> boolean jsonEquals(
            JsonModel<A> modelA, A a, JsonModel<B> modelB, B b) {
        return Values.jsonEquals(
                (JsonModel<@Nullable Object>) modelA,
                a,
                (JsonModel<@Nullable Object>) modelB,
                b,
                EvaluationLimits.DEFAULT.maxDepth());
    }

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

    /**
     * Returns the value of a number, or {@code null} if it is not finite (NaN or an infinity). JSON cannot
     * express such numbers, but parsers produce them, for example for {@code 1e400} as a {@code double}.
     * Queries treat them as neither equal to nor ordered against any value.
     */
    @Nullable
    BigDecimal numberValue(N value);

    /** Returns the value of a boolean. */
    boolean booleanValue(N value);

    /**
     * Returns the members of an object as name-value pairs, in the order of {@link #memberNames}.
     *
     * <p>The default implementation looks up each name with {@link #member}. Override it if the object
     * model can iterate its entries directly.
     */
    default Iterable<Map.Entry<String, N>> members(N object) {
        return () -> new Iterator<>() {
            private final Iterator<String> names = memberNames(object).iterator();

            @Override
            public boolean hasNext() {
                return names.hasNext();
            }

            @Override
            public Map.Entry<String, N> next() {
                String name = names.next();
                return new AbstractMap.SimpleImmutableEntry<>(name, member(object, name));
            }
        };
    }

    /**
     * Returns the value of the member with the given name, or {@code null} if the object has no such
     * member.
     *
     * <p>Models that represent JSON {@code null} as Java {@code null}, such as {@link JavaObjectModel},
     * may also return {@code null} for a member whose value is JSON null; callers then ask
     * {@link #hasMember} to tell the two apart. The default implementation calls {@link #hasMember} and
     * {@link #member}. Override it if one lookup is enough.
     */
    default @Nullable N findMember(N object, String name) {
        return hasMember(object, name) ? member(object, name) : null;
    }

    /**
     * Returns whether a number is an integer that {@link #longValue} returns exactly. Comparing two such
     * numbers needs no {@link BigDecimal}. The default returns {@code false}, so queries always use
     * {@link #numberValue}.
     */
    default boolean isLong(N number) {
        return false;
    }

    /**
     * Returns a number for which {@link #isLong} returned {@code true}, as a {@code long}.
     *
     * @throws UnsupportedOperationException if the model does not override {@link #isLong}
     */
    default long longValue(N number) {
        throw new UnsupportedOperationException("isLong() returned false");
    }
}
