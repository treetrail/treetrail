package io.github.treetrail.jsonpath.spring;

import io.github.treetrail.jsonpath.InvalidJsonException;
import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.NodeList;
import java.util.Arrays;
import java.util.List;

/**
 * The checks behind the MockMvc and WebTestClient matchers, on a response body as text. Values are compared
 * as JSON values ({@link JavaObjectModel#jsonEquals}): numbers by value, objects by members.
 */
final class JsonPathExpectation {

    private final String expression;
    private final JsonPath path;

    /** Compiles the expression right away, so that a mistake in the test fails where it is written. */
    JsonPathExpectation(String expression) {
        this.expression = expression;
        this.path = JsonPath.compile(expression);
    }

    void value(String body, Object expected) {
        NodeList<Object> nodes = select(body);
        if (nodes.size() != 1 || !JavaObjectModel.jsonEquals(nodes.get(0).value(), expected)) {
            throw fail("expected one node with value " + format(expected) + " but found " + describe(nodes));
        }
    }

    void values(String body, Object... expected) {
        NodeList<Object> nodes = select(body);
        List<Object> actual = nodes.values();
        boolean equal = actual.size() == expected.length;
        for (int i = 0; equal && i < expected.length; i++) {
            equal = JavaObjectModel.jsonEquals(actual.get(i), expected[i]);
        }
        if (!equal) {
            throw fail("expected values " + format(Arrays.asList(expected)) + " but found " + describe(nodes));
        }
    }

    void exists(String body) {
        if (select(body).isEmpty()) {
            throw fail("expected at least one node but found none");
        }
    }

    void doesNotExist(String body) {
        NodeList<Object> nodes = select(body);
        if (!nodes.isEmpty()) {
            throw fail("expected no node but found " + describe(nodes));
        }
    }

    void hasSize(String body, int size) {
        NodeList<Object> nodes = select(body);
        if (nodes.size() != size) {
            throw fail("expected " + size + " node(s) but found " + describe(nodes));
        }
    }

    private NodeList<Object> select(String body) {
        try {
            return path.query(JavaObjectModel.parse(body));
        } catch (InvalidJsonException e) {
            throw fail("the response body is not valid JSON: " + e.getMessage());
        }
    }

    private AssertionError fail(String message) {
        return new AssertionError("JSON path \"" + expression + "\": " + message);
    }

    private static String describe(NodeList<Object> nodes) {
        return nodes.isEmpty() ? "none" : format(nodes.values()) + " at " + nodes.paths();
    }

    private static String format(Object value) {
        return value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
    }
}
