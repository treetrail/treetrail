package io.github.treetrail.jsonpath.assertj;

import io.github.treetrail.jsonpath.InvalidJsonException;
import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.NodeList;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/**
 * Assertions on a JSON document. Created by {@link JsonPathAssertions#assertThatJson(String)}.
 */
public final class JsonAssert extends AbstractAssert<JsonAssert, String> {

    private @Nullable Object document;
    private boolean parsed;

    JsonAssert(String json) {
        super(json, JsonAssert.class);
    }

    /**
     * Selects the nodes of a JSONPath query and continues with assertions on their values.
     *
     * @throws io.github.treetrail.jsonpath.JsonPathSyntaxException if {@code expression} is not a valid
     *         RFC 9535 query, a mistake in the test rather than a failed assertion
     */
    public NodeListAssert jsonPath(String expression) {
        NodeList<@Nullable Object> nodes = JsonPath.compile(expression).query(document());
        return new NodeListAssert(nodes, expression).as(descriptionText().isEmpty() ? expression : descriptionText());
    }

    /** Verifies that the query selects at least one node. */
    public JsonAssert hasJsonPath(String expression) {
        if (JsonPath.compile(expression).query(document()).isEmpty()) {
            throw failure(
                    "Expected %s to select at least one node, but it selected none in:%n  %s", expression, actual);
        }
        return this;
    }

    /** Verifies that the query selects no node. */
    public JsonAssert doesNotHaveJsonPath(String expression) {
        NodeList<@Nullable Object> nodes = JsonPath.compile(expression).query(document());
        if (!nodes.isEmpty()) {
            throw failure(
                    "Expected %s to select no node, but it selected %s at %s",
                    expression, nodes.values(), nodes.paths());
        }
        return this;
    }

    private @Nullable Object document() {
        isNotNull();
        if (!parsed) {
            try {
                document = JavaObjectModel.parse(actual);
                parsed = true;
            } catch (InvalidJsonException e) {
                throw failure("Expected valid JSON, but %s:%n  %s", e.getMessage(), actual);
            }
        }
        return document;
    }
}
