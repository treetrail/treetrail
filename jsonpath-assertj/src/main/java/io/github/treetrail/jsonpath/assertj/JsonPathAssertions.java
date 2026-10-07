package io.github.treetrail.jsonpath.assertj;

import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.NodeList;

/**
 * Entry points for AssertJ assertions on JSON with JSONPath (RFC 9535).
 *
 * <pre>{@code
 * import static io.github.treetrail.jsonpath.assertj.JsonPathAssertions.assertThatJson;
 *
 * assertThatJson(responseBody).jsonPath("$.store.book[?@.price < 10].title")
 *         .containsExactly("Sayings of the Century", "Moby Dick");
 * assertThatJson(responseBody).jsonPath("$.store.bicycle.price").singleValue().isEqualTo(399);
 * assertThatJson(responseBody).doesNotHaveJsonPath("$.store.music");
 * }</pre>
 *
 * <p>Values are compared as JSON values: numbers by value, so {@code 399}, {@code 399L} and
 * {@code 399.0} match a JSON number {@code 399}, and objects by members regardless of order.
 */
public final class JsonPathAssertions {

    private JsonPathAssertions() {
    }

    /**
     * Starts assertions on JSON text, which is parsed with {@link JavaObjectModel#parse(String)}.
     *
     * @throws AssertionError if {@code json} is not valid JSON text
     */
    public static JsonAssert assertThatJson(String json) {
        return new JsonAssert(json);
    }

    /** Starts assertions on the result of a query, for example to check values and paths together. */
    public static NodeListAssert assertThat(NodeList<?> nodes) {
        return new NodeListAssert(nodes, "query result");
    }
}
