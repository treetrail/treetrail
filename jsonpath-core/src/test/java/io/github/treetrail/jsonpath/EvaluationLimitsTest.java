package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationLimitsTest {

    private static List<Object> numbers(int n) {
        List<Object> numbers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            numbers.add(i);
        }
        return numbers;
    }

    @Test
    void evaluatesAbsoluteQueriesInFiltersOncePerRun() {
        int n = 2_000;
        // Re-evaluating $[*] for every element would visit n * n = 4,000,000 nodes.
        JsonPath path = JsonPath.compile("$[?count($[*]) > 0]")
                .withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(3L * n));

        assertThat(path.query(numbers(n))).hasSize(n);
    }

    @Test
    void keepsNestedAbsoluteDescendantQueriesLinear() {
        int n = 500;
        List<Object> doc = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            doc.add(Map.of("v", List.of(i, i)));
        }
        // 4n nodes below the root; re-evaluating $..* for each of them would visit about 32n^2 nodes.
        JsonPath path = JsonPath.compile("$..[?count($..*) > 0]")
                .withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(20L * n));

        assertThat(path.query(doc)).hasSize(4 * n);
    }

    @Test
    void reusesAbsoluteResultsOnlyWithinOneRun() {
        JsonPath path = JsonPath.compile("$.items[?@ == $.target]");

        assertThat(path.query(Map.of("items", List.of(1, 2, 3), "target", 1)).values()).containsExactly(1);
        assertThat(path.query(Map.of("items", List.of(1, 2, 3), "target", 2)).values()).containsExactly(2);
    }

    @Test
    void failsWhenTooManyNodesAreVisited() {
        JsonPath path = JsonPath.compile("$[*]");

        assertThat(path.withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(1_000)).query(numbers(1_000)))
                .hasSize(1_000);
        assertThatThrownBy(() -> path.withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(999))
                .query(numbers(1_000)))
                .isInstanceOf(JsonPathLimitExceededException.class)
                .hasMessageContaining("999");
    }

    @Test
    void failsWhenTheResultIsTooLarge() {
        JsonPath path = JsonPath.compile("$[*]");

        assertThat(path.withLimits(EvaluationLimits.DEFAULT.withMaxResultSize(1_000)).query(numbers(1_000)))
                .hasSize(1_000);
        assertThatThrownBy(() -> path.withLimits(EvaluationLimits.DEFAULT.withMaxResultSize(999))
                .query(numbers(1_000)))
                .isInstanceOf(JsonPathLimitExceededException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void stopsWhenTheThreadIsInterrupted() {
        JsonPath path = JsonPath.compile("$..*");
        List<Object> doc = numbers(10_000);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> path.query(doc))
                    .isExactlyInstanceOf(JsonPathEvaluationException.class)
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).as("interrupt status stays set").isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void startsWithDefaultLimits() {
        JsonPath path = JsonPath.compile("$.a");
        EvaluationLimits strict = EvaluationLimits.DEFAULT.withMaxVisitedNodes(10).withMaxResultSize(1);

        assertThat(path.limits()).isEqualTo(EvaluationLimits.DEFAULT);
        assertThat(EvaluationLimits.DEFAULT.maxVisitedNodes()).isEqualTo(100_000_000L);
        assertThat(EvaluationLimits.DEFAULT.maxResultSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(path.withLimits(strict).limits()).isEqualTo(strict);
        assertThat(path.limits()).as("withLimits returns a copy").isEqualTo(EvaluationLimits.DEFAULT);
    }

    @Test
    void rejectsNegativeLimits() {
        assertThatThrownBy(() -> EvaluationLimits.DEFAULT.withMaxVisitedNodes(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvaluationLimits.DEFAULT.withMaxResultSize(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
