package io.github.treetrail.jsonpath.spring;

/**
 * JSONPath (RFC 9535) checks for Spring's {@code WebTestClient}, the counterpart of
 * {@code BodyContentSpec.jsonPath(...)}, which uses Jayway JsonPath.
 *
 * <pre>{@code
 * import static io.github.treetrail.jsonpath.spring.TreetrailWebTestClient.jsonPath;
 *
 * webTestClient.get().uri("/store").exchange()
 *         .expectBody()
 *         .consumeWith(jsonPath("$.store.bicycle.color").value("red"))
 *         .consumeWith(jsonPath("$.store.music").doesNotExist());
 * }</pre>
 *
 * <p>The checks mean the same as in {@link TreetrailResultMatchers}.
 */
public final class TreetrailWebTestClient {

    private TreetrailWebTestClient() {
    }

    /**
     * Returns checks for the nodes the expression selects in the response body.
     *
     * @throws io.github.treetrail.jsonpath.JsonPathSyntaxException if {@code expression} is not a valid
     *         RFC 9535 query
     */
    public static JsonPathBodyChecks jsonPath(String expression) {
        return new JsonPathBodyChecks(new JsonPathExpectation(expression));
    }
}
