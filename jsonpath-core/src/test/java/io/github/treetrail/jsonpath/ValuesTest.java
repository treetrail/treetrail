package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.treetrail.jsonpath.testkit.ComplianceCase;
import io.github.treetrail.jsonpath.testkit.JsonModelTestKit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** {@link JsonPath#values} selects what {@code query(...).values()} selects, and fails as it does. */
class ValuesTest {

    @Test
    void agreesWithQueryForEveryCaseOfTheComplianceSuite() {
        int cases = 0;
        for (ComplianceCase test : JsonModelTestKit.complianceCases()) {
            if (test.invalidSelector()) {
                continue;
            }
            JsonPath path = JsonPath.compile(test.selector());
            Object document = test.document();
            assertSameValues(path.values(document), path.query(document).values(), test.name());
            cases++;
        }
        assertThat(cases).isGreaterThan(400);
    }

    @Test
    void agreesWithQueryOnRandomQueries() {
        RandomQueries random = new RandomQueries(80);
        int compared = 0;
        for (int i = 0; i < 2_000; i++) {
            Object document = random.document();
            String query = random.query();
            JsonPath path;
            try {
                path = JsonPath.compile(query);
            } catch (JsonPathSyntaxException e) {
                continue;
            }
            assertSameValues(path.values(document), path.query(document).values(), query);
            assertThat(path.exists(document))
                    .as(query)
                    .isEqualTo(!path.query(document).isEmpty());
            compared++;
        }
        assertThat(compared).isGreaterThan(1_000);
    }

    @Test
    void selectsNullValues() {
        Map<String, @Nullable Object> document = new HashMap<>();
        document.put("a", null);
        document.put("b", List.of(Arrays.asList(null, 1), Map.of("c", 2)));

        assertThat(JsonPath.compile("$.a").values(document)).containsExactly((Object) null);
        assertThat(JsonPath.compile("$.a").exists(document)).isTrue();
        assertThat(JsonPath.compile("$.x").exists(document)).isFalse();
        assertThat(JsonPath.compile("$.b[0][0]").values(document)).containsExactly((Object) null);
        assertThat(JsonPath.compile("$[?@.a]").values(List.of(document))).containsExactly(document);
        assertThat(JsonPath.compile("$[?@.a == null]").values(List.of(document)))
                .containsExactly(document);
        assertThat(JsonPath.compile("$.b[?@[0] == null]").values(document)).hasSize(1);
        assertThat(JsonPath.compile("$..[?@ == null]").values(document)).hasSize(2);
    }

    @Test
    void returnsAnUnmodifiableList() {
        List<@Nullable Object> values = JsonPath.compile("$[*]").values(List.of(1, 2));
        assertThat(values).containsExactly(1, 2);
        assertThatThrownBy(() -> values.add(3)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reportsFailuresWithThePathOfTheNode() {
        Object notJson = new Object();
        Object document = Map.of("a", List.of(1, Map.of("b", notJson)));
        for (String query : List.of(
                "$..*", "$.a[*].b.c", "$.a[*].b[0:1]", "$.a[?@.b == 1]", "$.a[1][?@ > 1]", "$..[?length(@) > 1]")) {
            JsonPath path = JsonPath.compile(query);
            JsonPathEvaluationException expected =
                    catchThrowableOfType(JsonPathEvaluationException.class, () -> path.query(document));
            assertThat(expected).as(query).isNotNull();

            assertThatThrownBy(() -> path.values(document))
                    .as(query)
                    .isExactlyInstanceOf(JsonPathEvaluationException.class)
                    .hasMessage(expected.getMessage())
                    .extracting(e -> ((JsonPathEvaluationException) e).path())
                    .isEqualTo(expected.path());
        }
        // exists stops at the first node, so it fails only where no node comes before the failing one.
        assertThatThrownBy(() -> JsonPath.compile("$.a[1][?@ > 1]").exists(document))
                .isExactlyInstanceOf(JsonPathEvaluationException.class)
                .extracting(e -> ((JsonPathEvaluationException) e).path())
                .isEqualTo("$['a'][1]['b']");
    }

    @Test
    void reportsFailuresOfFunctionExtensions() {
        JsonPath path = JsonPath.compiler()
                .withFunctions(FunctionExtension.logical("fails", List.of(FunctionType.VALUE), arguments -> {
                    throw new IllegalStateException("fails on purpose");
                }))
                .compile("$.a[?fails(@)]");
        Object document = Map.of("a", List.of(1));

        assertThatThrownBy(() -> path.values(document))
                .isInstanceOf(JsonPathEvaluationException.class)
                .hasMessage("fails on purpose at $['a'][0]");
    }

    @Test
    void keepsTheLimits() {
        Object document = nested(50);
        JsonPath visits = JsonPath.compile("$..*").withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(10));
        JsonPath results = JsonPath.compile("$..*").withLimits(EvaluationLimits.DEFAULT.withMaxResultSize(10));
        JsonPath depth = JsonPath.compile("$..*").withLimits(EvaluationLimits.DEFAULT.withMaxDepth(10));
        for (JsonPath path : List.of(visits, results, depth)) {
            String expected = catchThrowableOfType(JsonPathLimitExceededException.class, () -> path.query(document))
                    .getMessage();
            assertThatThrownBy(() -> path.values(document))
                    .isInstanceOf(JsonPathLimitExceededException.class)
                    .hasMessage(expected);
        }
    }

    @Test
    void worksWithOtherModels() {
        JsonPath path = JsonPath.compile("$.a[*]");
        assertThat(path.values(Map.of("a", List.of(1, 2)), JavaObjectModel.INSTANCE))
                .containsExactly(1, 2);
        assertThat(path.values(Map.of("a", 1))).isEmpty();
    }

    private static void assertSameValues(List<?> actual, List<?> expected, String description) {
        assertThat(actual).as(description).hasSameSizeAs(expected);
        for (int i = 0; i < actual.size(); i++) {
            assertThat(actual.get(i)).as(description).isSameAs(expected.get(i));
        }
    }

    private static Object nested(int depth) {
        Object value = 1;
        for (int i = 0; i < depth; i++) {
            List<Object> array = new ArrayList<>();
            array.add(value);
            array.add(i);
            value = array;
        }
        return value;
    }
}
