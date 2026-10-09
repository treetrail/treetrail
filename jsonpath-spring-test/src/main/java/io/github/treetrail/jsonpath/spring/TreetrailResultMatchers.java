package io.github.treetrail.jsonpath.spring;

/**
 * JSONPath (RFC 9535) matchers for Spring MockMvc, the counterpart of
 * {@code MockMvcResultMatchers.jsonPath(...)}, which uses Jayway JsonPath.
 *
 * <pre>{@code
 * import static io.github.treetrail.jsonpath.spring.TreetrailResultMatchers.jsonPath;
 *
 * mockMvc.perform(get("/store"))
 *         .andExpect(jsonPath("$.store.bicycle.color").value("red"))
 *         .andExpect(jsonPath("$.store.book[?@.price < 10].title").values("Sayings of the Century", "Moby Dick"))
 *         .andExpect(jsonPath("$.store.music").doesNotExist());
 * }</pre>
 *
 * <p>Unlike Jayway, a query always selects a list of nodes, so {@code value(...)} means "exactly one node
 * with this value" and {@code values(...)} "exactly these values in this order". The response body is
 * read as UTF-8 and parsed with {@link io.github.treetrail.jsonpath.JavaObjectModel#parse(String)}.
 */
public final class TreetrailResultMatchers {

    private TreetrailResultMatchers() {}

    /**
     * Returns matchers for the nodes the expression selects in the response body.
     *
     * @throws io.github.treetrail.jsonpath.JsonPathSyntaxException if {@code expression} is not a valid
     *         RFC 9535 query
     */
    public static JsonPathResultMatchers jsonPath(String expression) {
        return new JsonPathResultMatchers(new JsonPathExpectation(expression));
    }
}
