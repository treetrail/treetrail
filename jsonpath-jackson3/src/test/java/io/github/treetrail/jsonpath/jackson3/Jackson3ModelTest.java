package io.github.treetrail.jsonpath.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathEvaluationException;
import io.github.treetrail.jsonpath.NodeList;
import io.github.treetrail.jsonpath.testkit.JsonModelTestKit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class Jackson3ModelTest {

    // Default settings on purpose: this is how most applications parse JSON.
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @TestFactory
    Stream<DynamicTest> complianceTestSuite() {
        return JsonModelTestKit.complianceTests(Jackson3Model.INSTANCE, MAPPER::readTree);
    }

    @TestFactory
    Stream<DynamicTest> modelContract() {
        return JsonModelTestKit.contractTests(Jackson3Model.INSTANCE, MAPPER::readTree);
    }

    @Test
    void returnsTheOriginalNodes() {
        JsonNode document = MAPPER.readTree(
                "{\"store\":{\"book\":[{\"title\":\"A\",\"price\":8.95},{\"title\":\"B\",\"price\":12}]}}");

        NodeList<JsonNode> nodes =
                JsonPath.compile("$.store.book[?@.price < 10]").query(document, Jackson3Model.INSTANCE);

        // Identity, not equality: the query hands back the library's own node objects.
        assertThat(nodes.values()).hasSize(1);
        assertThat(nodes.values().get(0))
                .isSameAs(document.get("store").get("book").get(0));
    }

    @Test
    void comparesIntegerAndFloatingPointNodesByValue() {
        JsonNode document = MAPPER.readTree("[{\"n\":1},{\"n\":1.0},{\"n\":10e-1},{\"n\":2}]");

        assertThat(JsonPath.compile("$[?@.n == 1]").query(document, Jackson3Model.INSTANCE))
                .hasSize(3);
    }

    @Test
    void rejectsNodesThatAreNotJson() {
        ObjectNode document = JsonNodeFactory.instance.objectNode();
        document.putPOJO("pojo", new Object());

        assertThatThrownBy(() -> JsonPath.compile("$[?@ == 1]").query(document, Jackson3Model.INSTANCE))
                .isInstanceOfSatisfying(
                        JsonPathEvaluationException.class,
                        e -> assertThat(e.path()).isEqualTo("$['pojo']"));
    }

    @Test
    void treatsInfiniteDoublesAsIncomparable() {
        // Jackson parses 1e400 into a DoubleNode holding Infinity.
        JsonNode document = MAPPER.readTree("[1e400, -1e400, 2]");

        assertThat(JsonPath.compile("$[?@ > 1]")
                        .query(document, Jackson3Model.INSTANCE)
                        .values())
                .containsExactly(document.get(2));
        assertThat(JsonPath.compile("$[?@ != 2]").query(document, Jackson3Model.INSTANCE))
                .hasSize(2);
    }
}
