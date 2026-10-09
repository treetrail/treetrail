package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonPathTest {

    private static final Map<String, Object> STORE = Map.of("store", Map.of(
            "book", List.of(
                    book("Sayings of the Century", 8.95),
                    book("Sword of Honour", 12.99),
                    book("Moby Dick", 8.99)),
            "bicycle", Map.of("color", "red", "price", 399)));

    private static Map<String, Object> book(String title, double price) {
        Map<String, Object> book = new LinkedHashMap<>();
        book.put("title", title);
        book.put("price", price);
        return book;
    }

    @Test
    void selectsValuesWithFilter() {
        NodeList<Object> nodes = JsonPath.compile("$.store.book[?@.price < 10].title").query(STORE);

        assertThat(nodes.values()).containsExactly("Sayings of the Century", "Moby Dick");
        assertThat(nodes.paths()).containsExactly(
                "$['store']['book'][0]['title']", "$['store']['book'][2]['title']");
    }

    @Test
    void comparesNumbersOfDifferentJavaTypesByValue() {
        Map<String, Object> doc = Map.of("items", List.of(
                Map.of("n", 1), Map.of("n", 1L), Map.of("n", 1.0), Map.of("n", new BigDecimal("1.00")),
                Map.of("n", 2)));

        assertThat(JsonPath.compile("$.items[?@.n == 1]").query(doc)).hasSize(4);
    }

    @Test
    void alwaysReturnsANodeListEvenForSingularQueries() {
        NodeList<Object> nodes = JsonPath.compile("$.store.bicycle.color").query(STORE);

        assertThat(nodes.values()).containsExactly("red");
        assertThat(nodes.single()).map(Node::value).contains("red");
    }

    @Test
    void missingMemberSelectsNothing() {
        NodeList<Object> nodes = JsonPath.compile("$.store.missing").query(STORE);

        assertThat(nodes).isEmpty();
        assertThat(nodes.single()).isEmpty();
        assertThat(nodes.first()).isEmpty();
    }

    @Test
    void distinguishesJsonNullFromMissingMember() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("a", null);

        Optional<Node<Object>> node = JsonPath.compile("$.a").query(doc).single();

        assertThat(node).isPresent();
        assertThat(node.get().value()).isNull();
        assertThat(node.get().path()).isEqualTo("$['a']");
        assertThat(JsonPath.compile("$.b").query(doc).single()).isEmpty();
    }

    @Test
    void returnsTheFirstOfSeveralNodes() {
        NodeList<Object> nodes = JsonPath.compile("$.store.book[*].title").query(STORE);

        assertThat(nodes.first()).map(Node::value).contains("Sayings of the Century");
    }

    @Test
    void singleRejectsMultipleNodes() {
        NodeList<Object> nodes = JsonPath.compile("$.store.book[*].title").query(STORE);

        assertThatThrownBy(nodes::single).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsLocationSteps() {
        Node<Object> node = JsonPath.compile("$.store.book[1].price").query(STORE).get(0);

        assertThat(node.location()).containsExactly("store", "book", 1, "price");
    }

    @Test
    void escapesNormalizedPaths() {
        Map<String, Object> doc = Map.of("it's\n", 1);

        assertThat(JsonPath.compile("$.*").query(doc).paths()).containsExactly("$['it\\'s\\n']");
    }

    @Test
    void knowsSingularQueries() {
        assertThat(JsonPath.compile("$.a['b'][0]").isSingular()).isTrue();
        assertThat(JsonPath.compile("$.a[*]").isSingular()).isFalse();
        assertThat(JsonPath.compile("$..a").isSingular()).isFalse();
    }

    @Test
    void reportsPositionOfSyntaxErrors() {
        assertThatThrownBy(() -> JsonPath.compile("$.store[?@.price <]"))
                .isInstanceOfSatisfying(JsonPathSyntaxException.class, e -> {
                    assertThat(e.position()).isEqualTo(18);
                    assertThat(e.expression()).isEqualTo("$.store[?@.price <]");
                });
    }

    @Test
    void rejectsDeeplyNestedExpressions() {
        String nested = "(".repeat(1000) + "@.a" + ")".repeat(1000);

        assertThatThrownBy(() -> JsonPath.compile("$[?" + nested + "]"))
                .isInstanceOf(JsonPathSyntaxException.class)
                .hasMessageContaining("nested");
    }

    @Test
    void rejectsJaywayOnlySyntax() {
        // Jayway JsonPath accepts these; RFC 9535 does not.
        assertThatThrownBy(() -> JsonPath.compile("$.book[?(@.price < 10)].length()"))
                .isInstanceOf(JsonPathSyntaxException.class);
        assertThatThrownBy(() -> JsonPath.compile("$.book[?@.tags in ['a']]"))
                .isInstanceOf(JsonPathSyntaxException.class);
        assertThatThrownBy(() -> JsonPath.compile("$.book[?@.title =~ /a.*/]"))
                .isInstanceOf(JsonPathSyntaxException.class);
    }

    @Test
    void namesTheAdapterWhenAJacksonTreeIsPassedAsPlainJavaObjects() throws Exception {
        Object tree = new ObjectMapper().readTree("{\"a\": 1}");

        assertThatThrownBy(() -> JsonPath.compile("$.a").query(tree))
                .isInstanceOf(JsonPathEvaluationException.class)
                .hasMessageContaining("ObjectNode")
                .hasMessageContaining("Jackson2Model.INSTANCE");
    }

    @Test
    void reportsOtherUnsupportedTypesWithoutHint() {
        assertThatThrownBy(() -> JsonPath.compile("$.a.b").query(Map.of("a", new Object())))
                .isInstanceOf(JsonPathEvaluationException.class)
                .hasMessage("Not a JSON value: java.lang.Object at $['a']");
    }

    @Test
    void showsAnExcerptWithACaretInSyntaxErrors() {
        assertThatThrownBy(() -> JsonPath.compile("$.store[?@.price <]"))
                .hasMessage("""
                        Expected a query, a literal or a function at position 18:
                          $.store[?@.price <]
                                            ^""");
    }

    @Test
    void shortensLongExpressionsInSyntaxErrors() {
        String expression = "$." + "a".repeat(200) + "[?@.x <]" + ".b".repeat(100);

        assertThatThrownBy(() -> JsonPath.compile(expression))
                .isInstanceOfSatisfying(JsonPathSyntaxException.class, e -> {
                    assertThat(e.expression()).isEqualTo(expression);
                    assertThat(e.reason()).isEqualTo("Expected a query, a literal or a function");
                    List<String> lines = e.getMessage().lines().toList();
                    assertThat(lines).hasSize(3);
                    assertThat(lines.get(1)).startsWith("  ...").endsWith("...").hasSize(2 + 3 + 60 + 3);
                    assertThat(lines.get(1).charAt(lines.get(2).indexOf('^'))).isEqualTo(']');
                });
    }

    @Test
    void keepsTheCaretAlignedWhenTheExpressionContainsLineBreaks() {
        assertThatThrownBy(() -> JsonPath.compile("$[?@.a ==\n\t]"))
                .isInstanceOfSatisfying(JsonPathSyntaxException.class, e -> {
                    List<String> lines = e.getMessage().lines().toList();
                    assertThat(lines.get(1)).isEqualTo("  $[?@.a ==  ]");
                    assertThat(lines.get(2).indexOf('^')).isEqualTo(2 + e.position());
                });
    }

    @Test
    void pointsTypeErrorsAtTheOperand() {
        assertThatThrownBy(() -> JsonPath.compile("$[?length(@.a) && @.b]"))
                .isInstanceOfSatisfying(JsonPathSyntaxException.class,
                        e -> assertThat(e.position()).isEqualTo(3));
        assertThatThrownBy(() -> JsonPath.compile("$[?@.b || !length(@.a)]"))
                .isInstanceOfSatisfying(JsonPathSyntaxException.class,
                        e -> assertThat(e.position()).isEqualTo(11));
    }
}
