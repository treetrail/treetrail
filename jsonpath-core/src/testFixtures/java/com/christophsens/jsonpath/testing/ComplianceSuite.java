package com.christophsens.jsonpath.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.christophsens.jsonpath.JsonModel;
import com.christophsens.jsonpath.JsonPath;
import com.christophsens.jsonpath.JsonPathSyntaxException;
import com.christophsens.jsonpath.NodeList;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

/**
 * Runs the JSONPath Compliance Test Suite (see {@code cts/README.md}) against one {@link JsonModel}.
 *
 * <p>Each test document is handed to the model's own JSON library as text, so the suite checks the
 * adapter as well as the query engine. Results are serialized back to JSON and compared by value.
 */
public final class ComplianceSuite {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private ComplianceSuite() {
    }

    /**
     * Creates one dynamic test per test case.
     *
     * @param model the model under test
     * @param parse parses JSON text into the model's node type
     * @param serialize writes a node of the model as JSON text
     */
    public static <N> Stream<DynamicTest> tests(JsonModel<N> model, Function<String, N> parse,
            Function<N, String> serialize) {
        return loadTests().stream().map(test -> DynamicTest.dynamicTest(
                test.get("name") + " | " + test.get("selector"), () -> run(test, model, parse, serialize)));
    }

    /**
     * Returns the raw test cases: maps with {@code name}, {@code selector}, {@code document},
     * {@code result} or {@code results}, and {@code invalid_selector}.
     */
    public static List<Map<String, Object>> cases() {
        return loadTests();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadTests() {
        try (InputStream in = ComplianceSuite.class.getResourceAsStream("/cts/cts.json")) {
            Map<String, Object> cts = MAPPER.readValue(in, Map.class);
            return (List<Map<String, Object>>) cts.get("tests");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <N> void run(Map<String, Object> test, JsonModel<N> model, Function<String, N> parse,
            Function<N, String> serialize) throws JsonProcessingException {
        String selector = (String) test.get("selector");
        if (Boolean.TRUE.equals(test.get("invalid_selector"))) {
            assertThatThrownBy(() -> JsonPath.compile(selector))
                    .as("selector should be rejected")
                    .isInstanceOf(JsonPathSyntaxException.class);
            return;
        }
        N document = parse.apply(MAPPER.writeValueAsString(test.get("document")));
        NodeList<N> nodes = JsonPath.compile(selector).query(document, model);
        List<Object> actual = new ArrayList<>();
        for (N value : nodes.values()) {
            actual.add(MAPPER.readValue(serialize.apply(value), Object.class));
        }
        if (test.containsKey("result")) {
            assertThat(jsonEquals(actual, test.get("result")))
                    .as("values: expected %s but got %s", test.get("result"), actual)
                    .isTrue();
            if (test.containsKey("result_paths")) {
                assertThat(nodes.paths()).isEqualTo(test.get("result_paths"));
            }
            return;
        }
        List<Object> alternatives = (List<Object>) test.get("results");
        List<Object> alternativePaths = (List<Object>) test.get("results_paths");
        for (int i = 0; i < alternatives.size(); i++) {
            boolean pathsMatch = alternativePaths == null || nodes.paths().equals(alternativePaths.get(i));
            if (jsonEquals(actual, alternatives.get(i)) && pathsMatch) {
                return;
            }
        }
        throw new AssertionError("No alternative matched: got " + actual + " at " + nodes.paths()
                + ", expected one of " + alternatives);
    }

    private static boolean jsonEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        if (a instanceof List && b instanceof List) {
            List<?> la = (List<?>) a;
            List<?> lb = (List<?>) b;
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!jsonEquals(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> ma = (Map<?, ?>) a;
            Map<?, ?> mb = (Map<?, ?>) b;
            if (!ma.keySet().equals(mb.keySet())) {
                return false;
            }
            for (Object key : ma.keySet()) {
                if (!jsonEquals(ma.get(key), mb.get(key))) {
                    return false;
                }
            }
            return true;
        }
        return a == null ? b == null : a.equals(b);
    }
}
