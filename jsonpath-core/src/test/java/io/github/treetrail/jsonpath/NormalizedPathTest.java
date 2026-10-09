package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.NormalizedPath.Index;
import io.github.treetrail.jsonpath.NormalizedPath.Name;
import io.github.treetrail.jsonpath.NormalizedPath.Step;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NormalizedPathTest {

    @Test
    void roundTripsTheLocationOfEveryNodeSelectedInTheComplianceSuite() {
        int nodes = 0;
        for (Map<String, Object> test : ComplianceSuite.cases()) {
            if (Boolean.TRUE.equals(test.get("invalid_selector"))) {
                continue;
            }
            for (Node<?> node : JsonPath.compile((String) test.get("selector")).query(test.get("document"))) {
                NormalizedPath path = NormalizedPath.parse(node.path());
                assertThat(path).as(node.path()).isEqualTo(node.normalizedPath());
                assertThat(path.toString()).isEqualTo(node.path());
                assertThat(asLocation(path.steps())).isEqualTo(node.location());
                nodes++;
            }
        }
        assertThat(nodes).isGreaterThan(600);
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesEveryResultPathOfTheComplianceSuiteBackToTheSameText() {
        List<String> paths = new ArrayList<>();
        for (Map<String, Object> test : ComplianceSuite.cases()) {
            if (test.get("result_paths") instanceof List<?> resultPaths) {
                paths.addAll((List<String>) resultPaths);
            }
            if (test.get("results_paths") instanceof List<?> alternatives) {
                alternatives.forEach(alternative -> paths.addAll((List<String>) alternative));
            }
        }
        assertThat(paths).hasSizeGreaterThan(500);
        for (String path : paths) {
            assertThat(NormalizedPath.parse(path).toString()).isEqualTo(path);
        }
    }

    @Test
    void hasTypedSteps() {
        NormalizedPath path = NormalizedPath.parse("$['store']['book'][0]");

        assertThat(path.steps()).containsExactly(new Name("store"), new Name("book"), new Index(0));
        assertThat(path)
                .isEqualTo(NormalizedPath.root().child("store").child("book").child(0))
                .isEqualTo(NormalizedPath.of(List.of(new Name("store"), new Name("book"), new Index(0))))
                .hasSameHashCodeAs(
                        NormalizedPath.root().child("store").child("book").child(0))
                .isNotEqualTo(NormalizedPath.parse("$['store']['book']['0']"));
        assertThat(NormalizedPath.parse("$")).isSameAs(NormalizedPath.root());
        assertThat(NormalizedPath.root().steps()).isEmpty();
    }

    @Test
    void convertsToAJsonPointer() {
        assertThat(NormalizedPath.root().toJsonPointer()).isEmpty();
        assertThat(NormalizedPath.parse("$['store']['book'][0]").toJsonPointer())
                .isEqualTo("/store/book/0");
        assertThat(NormalizedPath.parse("$['a/b']['m~n']['~1']").toJsonPointer())
                .isEqualTo("/a~1b/m~0n/~01");
        assertThat(NormalizedPath.parse("$['']").toJsonPointer()).isEqualTo("/");
        assertThat(NormalizedPath.parse("$['😀']").toJsonPointer()).isEqualTo("/😀");
    }

    @Test
    void escapesMemberNamesAsTheRfcPrescribes() {
        String name = "a'b\\c\b\f\n\r\t\u0000\u0007\u000b\u000e\u001f😀";
        NormalizedPath path = NormalizedPath.root().child(name);

        assertThat(path.toString()).isEqualTo("$['a\\'b\\\\c\\b\\f\\n\\r\\t\\u0000\\u0007\\u000b\\u000e\\u001f😀']");
        assertThat(NormalizedPath.parse(path.toString())).isEqualTo(path);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "@",
                "$.a",
                "$[\"a\"]",
                "$['a'",
                "$['a']x",
                "$[a]",
                "$[01]",
                "$[-1]",
                "$[2147483648]",
                "$['\\x']",
                "$['\\u000a']",
                "$['\\u001F']",
                "$['\\u0020']",
                "$['\\u12']",
                "$['\\'",
                "$['\n']",
                "$['\uD800']",
                "$[ 'a']"
            })
    void rejectsTextThatIsNotANormalizedPath(String text) {
        assertThatThrownBy(() -> NormalizedPath.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Not a normalized path: ");
    }

    @Test
    void rejectsNegativeIndices() {
        assertThatThrownBy(() -> NormalizedPath.root().child(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesQueriesByExpressionAndLimits() {
        JsonPath path = JsonPath.compile("$.a");

        assertThat(path.expression()).isEqualTo("$.a");
        assertThat(path).isEqualTo(JsonPath.compile("$.a")).hasSameHashCodeAs(JsonPath.compile("$.a"));
        assertThat(path).isNotEqualTo(JsonPath.compile("$['a']"));
        assertThat(path).isNotEqualTo(path.withLimits(EvaluationLimits.DEFAULT.withMaxDepth(10)));
    }

    @Test
    void hashesNodesByPath() {
        Node<?> first = JsonPath.compile("$.a").queryJson("{\"a\": [1, 2]}").get(0);
        Node<?> again = JsonPath.compile("$.a").queryJson("{\"a\": [1, 2]}").get(0);

        assertThat(first).isEqualTo(again).hasSameHashCodeAs(again);
        assertThat(first.hashCode()).isEqualTo(NormalizedPath.parse("$['a']").hashCode());
    }

    private static List<Object> asLocation(List<Step> steps) {
        List<Object> location = new ArrayList<>();
        for (Step step : steps) {
            location.add(step instanceof Index index ? (Object) index.index() : ((Name) step).name());
        }
        return location;
    }
}
