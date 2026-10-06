package io.github.treetrail.jsonpath.spring;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

/**
 * WebTestClient checks for the nodes of one JSONPath expression, for
 * {@code expectBody().consumeWith(...)}. Created by {@link TreetrailWebTestClient#jsonPath(String)}.
 */
public final class JsonPathBodyChecks {

    private final JsonPathExpectation expectation;

    JsonPathBodyChecks(JsonPathExpectation expectation) {
        this.expectation = expectation;
    }

    /** Expects exactly one node, with a value equal to {@code expected} as a JSON value. */
    public Consumer<EntityExchangeResult<byte[]>> value(Object expected) {
        return result -> expectation.value(body(result), expected);
    }

    /** Expects exactly these values, in this order. */
    public Consumer<EntityExchangeResult<byte[]>> values(Object... expected) {
        return result -> expectation.values(body(result), expected);
    }

    /** Expects at least one node. */
    public Consumer<EntityExchangeResult<byte[]>> exists() {
        return result -> expectation.exists(body(result));
    }

    /** Expects no node. */
    public Consumer<EntityExchangeResult<byte[]>> doesNotExist() {
        return result -> expectation.doesNotExist(body(result));
    }

    /** Expects exactly {@code size} nodes. */
    public Consumer<EntityExchangeResult<byte[]>> hasSize(int size) {
        return result -> expectation.hasSize(body(result), size);
    }

    private static String body(EntityExchangeResult<byte[]> result) {
        byte[] body = result.getResponseBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
