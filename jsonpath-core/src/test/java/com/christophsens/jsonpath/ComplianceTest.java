package com.christophsens.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the JSONPath Compliance Test Suite (see {@code src/test/resources/cts/README.md}).
 */
class ComplianceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @TestFactory
    Stream<DynamicTest> complianceTestSuite() throws IOException {
        return loadTests().stream().map(test -> DynamicTest.dynamicTest(
                (String) test.get("name") + " | " + test.get("selector"), () -> run(test)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadTests() throws IOException {
        try (InputStream in = ComplianceTest.class.getResourceAsStream("/cts/cts.json")) {
            Map<String, Object> cts = MAPPER.readValue(in, Map.class);
            return (List<Map<String, Object>>) cts.get("tests");
        }
    }

    @SuppressWarnings("unchecked")
    private static void run(Map<String, Object> test) {
        String selector = (String) test.get("selector");
        if (Boolean.TRUE.equals(test.get("invalid_selector"))) {
            assertThatThrownBy(() -> JsonPath.compile(selector))
                    .as("selector should be rejected")
                    .isInstanceOf(JsonPathSyntaxException.class);
            return;
        }
        NodeList<Object> nodes = JsonPath.compile(selector).query(test.get("document"));
        List<Object> actual = new ArrayList<>(nodes.values());
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
