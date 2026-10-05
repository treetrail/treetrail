package io.github.treetrail.jsonpath.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.NodeList;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
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
        return ComplianceSuite.tests(Jackson3Model.INSTANCE, MAPPER::readTree, JsonNode::toString);
    }

    @Test
    void returnsTheOriginalNodes() {
        JsonNode document = MAPPER.readTree(
                "{\"store\":{\"book\":[{\"title\":\"A\",\"price\":8.95},{\"title\":\"B\",\"price\":12}]}}");

        NodeList<JsonNode> nodes = JsonPath.compile("$.store.book[?@.price < 10]").query(document, Jackson3Model.INSTANCE);

        assertThat(nodes.values()).containsExactly(document.get("store").get("book").get(0));
    }

    @Test
    void comparesIntegerAndFloatingPointNodesByValue() {
        JsonNode document = MAPPER.readTree("[{\"n\":1},{\"n\":1.0},{\"n\":10e-1},{\"n\":2}]");

        assertThat(JsonPath.compile("$[?@.n == 1]").query(document, Jackson3Model.INSTANCE)).hasSize(3);
    }

    @Test
    void rejectsNodesThatAreNotJson() {
        ObjectNode document = JsonNodeFactory.instance.objectNode();
        document.putPOJO("pojo", new Object());

        assertThatThrownBy(() -> JsonPath.compile("$[?@ == 1]").query(document, Jackson3Model.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
