package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.testkit.ComplianceCase;
import io.github.treetrail.jsonpath.testkit.JsonModelTestKit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExistsAndFirstTest {

    @Test
    void agreeWithQueryForEveryCaseOfTheComplianceSuite() {
        int cases = 0;
        for (ComplianceCase test : JsonModelTestKit.complianceCases()) {
            if (test.invalidSelector()) {
                continue;
            }
            JsonPath path = JsonPath.compile(test.selector());
            Object document = test.document();
            NodeList<Object> all = path.query(document);
            Optional<Node<Object>> first = path.first(document);

            assertThat(path.exists(document)).as(test.name()).isEqualTo(!all.isEmpty());
            if (all.isEmpty()) {
                assertThat(first).as(test.name()).isEmpty();
            } else {
                assertThat(first).as(test.name()).contains(all.get(0));
            }
            cases++;
        }
        assertThat(cases).isGreaterThan(400);
    }

    @Test
    void stopAtTheFirstNode() {
        Object document = nested(2_000);
        JsonPath limited = JsonPath.compile("$..*").withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(10));

        assertThat(limited.exists(document)).isTrue();
        assertThat(limited.first(document)).map(Node::path).contains("$[0]");
        assertThatThrownBy(() -> limited.query(document)).isInstanceOf(JsonPathLimitExceededException.class);
    }

    @Test
    void existenceTestsInFiltersStopAtTheFirstNode() {
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(Map.of("children", nested(200)));
        }
        // Each filter test walks one item's 200 descendants until it finds the first; listing all of
        // them would visit 100 x 200 nodes and more.
        JsonPath path = JsonPath.compile("$[?@..*]").withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(1_000));

        assertThat(path.query(items)).hasSize(100);
    }

    @Test
    void workWithOtherModelsAndEmptyResults() {
        JsonPath path = JsonPath.compile("$.missing");

        assertThat(path.exists(Map.of("a", 1))).isFalse();
        assertThat(path.first(Map.of("a", 1))).isEmpty();
        assertThat(JsonPath.compile("$.a").first(Map.of("a", 1), JavaObjectModel.INSTANCE))
                .map(Node::value)
                .contains(1);
    }

    /** {@code [[[...[0]...]]]}: an array nested {@code depth} levels deep. */
    private static Object nested(int depth) {
        Object value = 0;
        for (int i = 0; i < depth; i++) {
            List<Object> array = new ArrayList<>();
            array.add(value);
            value = array;
        }
        return value;
    }
}
