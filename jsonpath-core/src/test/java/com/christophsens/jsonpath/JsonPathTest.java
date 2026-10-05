package com.christophsens.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        assertThat(nodes.single()).contains("red");
    }

    @Test
    void missingMemberSelectsNothing() {
        NodeList<Object> nodes = JsonPath.compile("$.store.missing").query(STORE);

        assertThat(nodes).isEmpty();
        assertThat(nodes.single()).isEmpty();
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
}
