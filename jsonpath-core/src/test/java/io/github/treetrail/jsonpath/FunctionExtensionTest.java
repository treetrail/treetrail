package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionExtensionTest {

    private static final String BOOKS = """
            {"book": [
              {"title": "A", "price": 8.95, "tags": ["x", "y"]},
              {"title": "B", "price": 12.99, "tags": []},
              {"title": "C", "price": 8.99, "tags": ["x"]}
            ]}
            """;

    /** The smallest number among the nodes, or Nothing. */
    private static final FunctionExtension MIN = FunctionExtension.value("min", List.of(FunctionType.NODES), args -> {
        BigDecimal smallest = null;
        for (FunctionValue v : args.nodes(0)) {
            if (v.kind() == JsonKind.NUMBER && (smallest == null || v.number().compareTo(smallest) < 0)) {
                smallest = v.number();
            }
        }
        return smallest == null ? FunctionValue.nothing() : FunctionValue.of(smallest);
    });

    private static final FunctionExtension STARTS_WITH = FunctionExtension.logical(
            "starts_with",
            List.of(FunctionType.VALUE, FunctionType.VALUE),
            args -> args.value(0).kind() == JsonKind.STRING
                    && args.value(0).string().startsWith(args.value(1).string()));

    /** The nodes whose value is a non-empty array. */
    private static final FunctionExtension NON_EMPTY =
            FunctionExtension.nodes("non_empty", List.of(FunctionType.NODES), args -> {
                List<FunctionValue> result = new ArrayList<>();
                for (FunctionValue v : args.nodes(0)) {
                    if (v.kind() == JsonKind.ARRAY && v.size() > 0) {
                        result.add(v);
                    }
                }
                return result;
            });

    private static final JsonPathCompiler COMPILER = JsonPath.compiler().withFunctions(MIN, STARTS_WITH, NON_EMPTY);

    @Test
    void valueFunctionsCompareInFilters() {
        NodeList<Object> cheapest = COMPILER.compile("$.book[?@.price == min($.book[*].price)].title")
                .queryJson(BOOKS);

        assertThat(cheapest.values()).containsExactly("A");
    }

    @Test
    void logicalFunctionsAreTests() {
        assertThat(COMPILER.compile("$.book[?starts_with(@.title, 'B')].price")
                        .queryJson(BOOKS)
                        .values())
                .containsExactly(new BigDecimal("12.99"));
    }

    @Test
    void nodesFunctionsAreTestsAndArgumentsOfNodesParameters() {
        assertThat(COMPILER.compile("$.book[?non_empty(@.tags)].title")
                        .queryJson(BOOKS)
                        .values())
                .containsExactly("A", "C");
        assertThat(COMPILER.compile("$[?count(non_empty(@[*].tags)) == 2]")
                        .queryJson(BOOKS)
                        .paths())
                .containsExactly("$['book']");
    }

    @Test
    void callsAreTypeCheckedWhenTheQueryIsCompiled() {
        assertThatThrownBy(() -> COMPILER.compile("$[?min('a') == 1]")).isInstanceOf(JsonPathSyntaxException.class);
        assertThatThrownBy(() -> COMPILER.compile("$[?min(@.a)]"))
                .isInstanceOf(JsonPathSyntaxException.class)
                .hasMessageContaining("returns a value and cannot be used as a test");
        assertThatThrownBy(() -> COMPILER.compile("$[?starts_with(@.a)]")).isInstanceOf(JsonPathSyntaxException.class);
        assertThatThrownBy(() -> JsonPath.compile("$[?min(@.a[*]) == 1]"))
                .isInstanceOf(JsonPathSyntaxException.class)
                .hasMessageContaining("Unknown function 'min'");
    }

    @Test
    void checksNamesAndDuplicates() {
        for (String name : List.of("", "Min", "1st", "min-value", "länge")) {
            assertThatThrownBy(() -> FunctionExtension.value(name, List.of(), args -> FunctionValue.nothing()))
                    .as(name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> FunctionExtension.value("length", List.of(), args -> FunctionValue.nothing()))
                .hasMessageContaining("built-in");
        assertThatThrownBy(() -> COMPILER.withFunctions(MIN)).hasMessageContaining("already known");
        assertThat(MIN.name()).isEqualTo("min");
        assertThat(MIN.parameters()).containsExactly(FunctionType.NODES);
        assertThat(MIN.result()).isEqualTo(FunctionType.VALUE);
        assertThat(NON_EMPTY.result()).isEqualTo(FunctionType.NODES);
        assertThat(STARTS_WITH.result()).isEqualTo(FunctionType.LOGICAL);
        assertThat(MIN).hasToString("min[NODES] -> VALUE");
    }

    @Test
    void reportsFailingFunctionsWithTheNodeBeingTested() {
        FunctionExtension failing = FunctionExtension.logical("fails", List.of(FunctionType.VALUE), args -> {
            throw new IllegalStateException("boom");
        });
        FunctionExtension returnsNull =
                FunctionExtension.value("returns_null", List.of(FunctionType.VALUE), args -> null);
        JsonPathCompiler compiler = JsonPath.compiler().withFunctions(failing, returnsNull);

        assertThatThrownBy(() -> compiler.compile("$.book[?fails(@.title)]").queryJson(BOOKS))
                .isInstanceOf(JsonPathEvaluationException.class)
                .hasMessage("boom at $['book'][0]");
        assertThatThrownBy(() ->
                        compiler.compile("$.book[?returns_null(@.title) == 1]").queryJson(BOOKS))
                .isInstanceOf(JsonPathEvaluationException.class)
                .hasMessageContaining("returned null; return FunctionValue.nothing()");
    }

    @Test
    void functionValuesReadTheirContent() {
        List<FunctionValue> seen = new ArrayList<>();
        FunctionExtension capture = FunctionExtension.logical("capture", List.of(FunctionType.VALUE), args -> {
            seen.add(args.value(0));
            return args.size() == 1;
        });
        JsonPath.compiler()
                .withFunctions(capture)
                .compile("$[?capture(@)]")
                .queryJson("[{\"a\": [1, \"x\"], \"b\": null}, 2.5, \"s\", true, null]");

        FunctionValue object = seen.get(0);
        assertThat(object.kind()).isEqualTo(JsonKind.OBJECT);
        assertThat(object.size()).isEqualTo(2);
        assertThat(object.memberNames()).containsExactly("a", "b");
        assertThat(object.member("b").kind()).isEqualTo(JsonKind.NULL);
        assertThat(object.member("missing").isNothing()).isTrue();
        FunctionValue array = object.member("a");
        assertThat(array.size()).isEqualTo(2);
        assertThat(array.element(0).number()).isEqualByComparingTo("1");
        assertThat(array.element(1).string()).isEqualTo("x");
        assertThatThrownBy(() -> array.element(2)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(seen.get(1).number()).isEqualByComparingTo("2.5");
        assertThat(seen.get(2).string()).isEqualTo("s");
        assertThat(seen.get(3).bool()).isTrue();
        assertThat(seen.get(4).kind()).isEqualTo(JsonKind.NULL);

        assertThatThrownBy(() -> seen.get(2).number()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seen.get(1).size()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FunctionValue.nothing().kind()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsResultValues() {
        assertThat(FunctionValue.nothing().isNothing()).isTrue();
        assertThat(FunctionValue.ofNull().kind()).isEqualTo(JsonKind.NULL);
        assertThat(FunctionValue.of("x").string()).isEqualTo("x");
        assertThat(FunctionValue.of(new BigDecimal("1.5")).number()).isEqualByComparingTo("1.5");
        assertThat(FunctionValue.of(7).number()).isEqualByComparingTo("7");
        assertThat(FunctionValue.of(true).bool()).isTrue();
    }

    @Test
    void queriesWithOtherFunctionsAreNotEqual() {
        assertThat(COMPILER.compile("$.a")).isNotEqualTo(JsonPath.compile("$.a"));
        assertThat(COMPILER.compile("$.a")).isEqualTo(COMPILER.compile("$.a"));
        assertThat(COMPILER.withLimits(EvaluationLimits.DEFAULT.withMaxDepth(5))
                        .compile("$.a")
                        .limits()
                        .maxDepth())
                .isEqualTo(5);
    }
}
