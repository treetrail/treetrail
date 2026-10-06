package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Documents that are not I-JSON: cycles, deep nesting, numbers that are not finite and Java types
 * beyond {@code Map} and {@code List}.
 */
class RobustnessTest {

    private static Object nestedLists(int depth) {
        Object value = "x";
        for (int i = 0; i < depth; i++) {
            value = List.of(value);
        }
        return value;
    }

    @Test
    void stopsAtTheDepthLimitWhenAMapContainsItself() {
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);

        assertThatThrownBy(() -> JsonPath.compile("$..*").query(cyclic))
                .isInstanceOf(JsonPathLimitExceededException.class)
                .hasMessageContaining("1000 levels");
    }

    @Test
    void stopsComparingCyclicValuesAtTheDepthLimit() {
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);

        assertThatThrownBy(() -> JsonPath.compile("$[?@.a == @.b]").query(List.of(Map.of("a", cyclic, "b", cyclic))))
                .isInstanceOf(JsonPathLimitExceededException.class)
                .hasMessageContaining("may contain itself");
    }

    @Test
    void walksDocumentsUpToTheDepthLimit() {
        assertThat(JsonPath.compile("$..*").query(nestedLists(1_000))).hasSize(1_000);
        assertThatThrownBy(() -> JsonPath.compile("$..*").query(nestedLists(1_001)))
                .isInstanceOf(JsonPathLimitExceededException.class);
    }

    @Test
    void comparesDeepValuesWithoutOverflowingTheStack() {
        Object deep = nestedLists(100_000);
        Object doc = List.of(Map.of("a", deep, "b", nestedLists(100_000)));
        JsonPath path = JsonPath.compile("$[?@.a == @.b]");

        assertThatThrownBy(() -> path.query(doc)).isInstanceOf(JsonPathLimitExceededException.class);
        assertThat(path.withLimits(EvaluationLimits.DEFAULT.withMaxDepth(200_000)).query(doc)).hasSize(1);
    }

    @Test
    void treatsNumbersThatAreNotFiniteAsIncomparable() {
        List<Object> doc = List.of(Double.POSITIVE_INFINITY, Double.NaN, Float.NEGATIVE_INFINITY, 2);

        assertThat(JsonPath.compile("$[?@ > 1]").query(doc).values()).containsExactly(2);
        assertThat(JsonPath.compile("$[?@ <= 2]").query(doc).values()).containsExactly(2);
        assertThat(JsonPath.compile("$[?@ == 2]").query(doc).values()).containsExactly(2);
        assertThat(JsonPath.compile("$[?@ != 2]").query(doc).values())
                .containsExactly(Double.POSITIVE_INFINITY, Double.NaN, Float.NEGATIVE_INFINITY);
    }

    @Test
    void handlesInfinityFromJacksonParsingAsPlainJavaObjects() throws Exception {
        Object doc = new ObjectMapper().readValue("[1e400, 2]", Object.class);

        assertThat(JsonPath.compile("$[?@ > 1]").query(doc).values()).containsExactly(2);
    }

    @Test
    void treatsJavaArraysAsJsonArrays() {
        Map<String, Object> doc = Map.of("names", new String[] {"x", "y"}, "numbers", new int[] {1, 2, 3});

        assertThat(JsonPath.compile("$.names[1]").query(doc).values()).containsExactly("y");
        assertThat(JsonPath.compile("$.numbers[?@ > 1]").query(doc).values()).containsExactly(2, 3);
        assertThat(JsonPath.compile("$[?length(@.numbers) == 3]").query(List.of(doc))).hasSize(1);
    }

    @Test
    void treatsCharactersAsStrings() {
        List<Object> doc = new ArrayList<>(Arrays.asList('x', "x", 'y'));

        assertThat(JsonPath.compile("$[?@ == 'x']").query(doc).values()).containsExactly('x', "x");
        assertThat(JsonPath.compile("$[?match(@, 'y')]").query(doc).values()).containsExactly('y');
    }

    @Test
    void rejectsCollectionsWithoutOrderWithAHint() {
        assertThatThrownBy(() -> JsonPath.compile("$.tags[0]").query(Map.of("tags", Set.of("a"))))
                .isInstanceOfSatisfying(JsonPathEvaluationException.class,
                        e -> assertThat(e.path()).isEqualTo("$['tags']"))
                .hasMessageContaining("copy them into a List")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMapKeysThatAreNotStrings() {
        assertThatThrownBy(() -> JsonPath.compile("$.*").query(Map.of(1, "x")))
                .isInstanceOfSatisfying(JsonPathEvaluationException.class, e -> assertThat(e.path()).isEqualTo("$"))
                .hasMessageContaining("Map keys must be strings")
                .hasMessageContaining("java.lang.Integer");
    }
}
