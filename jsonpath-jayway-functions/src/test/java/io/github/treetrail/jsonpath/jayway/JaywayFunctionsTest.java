package io.github.treetrail.jsonpath.jayway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.FunctionType;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathCompiler;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JaywayFunctionsTest {

    private static final JsonPathCompiler COMPILER = JaywayFunctions.compiler();

    private static final String ORDERS = """
            {"orders": [
              {"id": 1, "prices": [10, 20.5, "n/a"]},
              {"id": 2, "prices": [-3, -1]},
              {"id": 3, "prices": [2, 4, 4, 4, 5, 5, 7, 9]},
              {"id": 4, "prices": ["free"]},
              {"id": 5, "prices": [0.1, 0.2]}
            ]}
            """;

    private static List<Object> ids(String filter) {
        return COMPILER.compile("$.orders[?" + filter + "].id")
                .queryJson(ORDERS)
                .values();
    }

    @Test
    void aggregateTheNumbersAmongTheNodes() {
        assertThat(ids("min(@.prices[*]) == 10")).containsExactly(1);
        assertThat(ids("max(@.prices[*]) == 20.5")).containsExactly(1);
        assertThat(ids("sum(@.prices[*]) == 30.5")).containsExactly(1);
        assertThat(ids("avg(@.prices[*]) == 5")).containsExactly(3);
        assertThat(ids("stddev(@.prices[*]) == 2")).containsExactly(3);
    }

    @Test
    void workWithNegativeNumbersAndAreExact() {
        assertThat(ids("max(@.prices[*]) == -1")).containsExactly(2);
        assertThat(ids("min(@.prices[*]) == -3")).containsExactly(2);
        // 0.1 + 0.2 is exactly 0.3 in decimal; with double it would be 0.30000000000000004.
        assertThat(ids("sum(@.prices[*]) == 0.3")).containsExactly(5);
    }

    @Test
    void returnNothingWithoutNumbers() {
        assertThat(ids("min(@.prices[*]) < 1000")).containsExactly(1, 2, 3, 5);
        // As in RFC 9535 for any two Nothings, Nothing == Nothing is true.
        assertThat(ids("min(@.prices[*]) == min(@.missing[*])")).containsExactly(4);
        assertThat(ids("sum(@.missing[*]) == 0")).isEmpty();
        assertThat(ids("!(sum(@.prices[*]) >= 0) && !(sum(@.prices[*]) < 0)")).containsExactly(4);
    }

    @Test
    void skipNumbersThatAreNotFinite() {
        Map<String, Object> document = Map.of("values", Arrays.asList(1.0, Double.NaN, Double.POSITIVE_INFINITY, 3.0));

        assertThat(COMPILER.compile("$[?avg(@[*]) == 2]").query(document).paths())
                .containsExactly("$['values']");
    }

    @Test
    void areDeclaredAsNodesToValueFunctions() {
        assertThat(JaywayFunctions.ALL).extracting(f -> f.name()).containsExactly("min", "max", "sum", "avg", "stddev");
        assertThat(JaywayFunctions.ALL).allSatisfy(f -> {
            assertThat(f.parameters()).containsExactly(FunctionType.NODES);
            assertThat(f.result()).isEqualTo(FunctionType.VALUE);
        });
    }

    @Test
    void areOnlyKnownToCompilersThatHaveThem() {
        assertThatThrownBy(() -> JsonPath.compile("$[?sum(@[*]) > 1]")).isInstanceOf(JsonPathSyntaxException.class);
        assertThatThrownBy(() -> COMPILER.compile("$[?sum(@.a) ]")).isInstanceOf(JsonPathSyntaxException.class);
    }
}
