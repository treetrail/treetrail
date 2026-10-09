package io.github.treetrail.jsonpath.spring;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * MockMvc matchers for the nodes of one JSONPath expression. Created by
 * {@link TreetrailResultMatchers#jsonPath(String)}.
 */
public final class JsonPathResultMatchers {

    private final JsonPathExpectation expectation;

    JsonPathResultMatchers(JsonPathExpectation expectation) {
        this.expectation = expectation;
    }

    /** Expects exactly one node, with a value equal to {@code expected} as a JSON value. */
    public ResultMatcher value(@Nullable Object expected) {
        return result -> expectation.value(body(result), expected);
    }

    /** Expects exactly these values, in this order. */
    public ResultMatcher values(@Nullable Object... expected) {
        return result -> expectation.values(body(result), expected);
    }

    /** Expects at least one node. */
    public ResultMatcher exists() {
        return result -> expectation.exists(body(result));
    }

    /** Expects no node. */
    public ResultMatcher doesNotExist() {
        return result -> expectation.doesNotExist(body(result));
    }

    /** Expects exactly {@code size} nodes. */
    public ResultMatcher hasSize(int size) {
        return result -> expectation.hasSize(body(result), size);
    }

    private static String body(MvcResult result) throws java.io.UnsupportedEncodingException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
