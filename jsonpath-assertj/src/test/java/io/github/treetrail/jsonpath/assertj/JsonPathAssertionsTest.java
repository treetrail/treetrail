package io.github.treetrail.jsonpath.assertj;

import static io.github.treetrail.jsonpath.assertj.JsonPathAssertions.assertThat;
import static io.github.treetrail.jsonpath.assertj.JsonPathAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonPathAssertionsTest {

    private static final String STORE = "{\"store\":{"
            + "\"book\":[{\"title\":\"Sayings\",\"price\":8.95,\"tags\":[\"a\",\"b\"]},"
            + "{\"title\":\"Sword\",\"price\":12.99},{\"title\":\"Moby Dick\",\"price\":8.99,\"isbn\":null}],"
            + "\"bicycle\":{\"color\":\"red\",\"price\":399}}}";

    @Test
    void assertsSelectedValuesWithListAssertions() {
        assertThatJson(STORE).jsonPath("$.store.book[?@.price < 10].title").containsExactly("Sayings", "Moby Dick");
        assertThatJson(STORE).jsonPath("$.store.book[*]").hasSize(3);
        assertThatJson(STORE).jsonPath("$.store.music").isEmpty();
    }

    @Test
    void comparesNumbersByValueAndObjectsByMembers() {
        assertThatJson(STORE).jsonPath("$.store.bicycle.price").containsExactly(399L);
        assertThatJson(STORE).jsonPath("$.store.bicycle.price").singleValue().isEqualTo(399.0);
        assertThatJson(STORE).jsonPath("$.store.book[0].price").singleValue().isEqualTo(new BigDecimal("8.950"));
        assertThatJson(STORE).jsonPath("$.store.book[0].price").singleValue().isEqualTo(8.95);
        assertThatJson(STORE).jsonPath("$.store.bicycle").singleValue().isEqualTo(Map.of("price", 399, "color", "red"));
        assertThatJson(STORE).jsonPath("$.store.book[0].tags").singleValue().isEqualTo(List.of("a", "b"));
    }

    @Test
    void distinguishesJsonNullFromMissingMembers() {
        assertThatJson(STORE).jsonPath("$.store.book[2].isbn").singleValue().isNull();
        assertThatJson(STORE).hasJsonPath("$.store.book[2].isbn");
        assertThatJson(STORE).doesNotHaveJsonPath("$.store.book[0].isbn");
    }

    @Test
    void assertsNormalizedPaths() {
        assertThatJson(STORE).jsonPath("$..price").paths()
                .contains("$['store']['bicycle']['price']", "$['store']['book'][0]['price']");
        assertThat(JsonPath.compile("$.store.book[1].title").queryJson(STORE)).containsExactly("Sword");
    }

    @Test
    void namesTheExpressionWhenValuesDiffer() {
        assertThatThrownBy(() -> assertThatJson(STORE).jsonPath("$.store.book[*].title").containsExactly("Sayings"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.store.book[*].title")
                .hasMessageContaining("Sword");
    }

    @Test
    void reportsHowManyNodesWereSelectedWhenOneWasExpected() {
        assertThatThrownBy(() -> assertThatJson(STORE).jsonPath("$..price").singleValue())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("exactly one node")
                .hasMessageContaining("selected 4")
                .hasMessageContaining("$['store']['bicycle']['price']");
        assertThatThrownBy(() -> assertThatJson(STORE).jsonPath("$.store.music").singleValue())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("selected 0");
    }

    @Test
    void reportsPresenceAndAbsenceFailures() {
        assertThatThrownBy(() -> assertThatJson(STORE).hasJsonPath("$.store.music"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("at least one node");
        assertThatThrownBy(() -> assertThatJson(STORE).doesNotHaveJsonPath("$.store.bicycle.color"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("red")
                .hasMessageContaining("$['store']['bicycle']['color']");
    }

    @Test
    void failsOnInvalidJsonAndRejectsInvalidQueries() {
        assertThatThrownBy(() -> assertThatJson("{\"a\":").jsonPath("$.a"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected valid JSON");
        assertThatThrownBy(() -> assertThatJson(STORE).jsonPath("$.store.book[?(@.price < 10)].length()"))
                .isInstanceOf(JsonPathSyntaxException.class);
    }

    @Test
    void usesTheCustomDescription() {
        assertThatThrownBy(() -> assertThatJson(STORE).as("book titles").jsonPath("$.store.book[0].title")
                .containsExactly("Other"))
                .hasMessageContaining("[book titles]");
    }
}
