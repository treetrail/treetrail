package io.github.treetrail.jsonpath.jackson2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.NodeList;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class Jackson2ModelTest {

    // Default settings on purpose: this is how most applications parse JSON.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> complianceTestSuite() {
        return ComplianceSuite.tests(Jackson2Model.INSTANCE, Jackson2ModelTest::parse, JsonNode::toString);
    }

    @Test
    void returnsTheOriginalNodes() {
        JsonNode document = parse("{\"store\":{\"book\":[{\"title\":\"A\",\"price\":8.95},{\"title\":\"B\",\"price\":12}]}}");

        NodeList<JsonNode> nodes = JsonPath.compile("$.store.book[?@.price < 10]").query(document, Jackson2Model.INSTANCE);

        assertThat(nodes.values()).containsExactly(document.get("store").get("book").get(0));
    }

    @Test
    void comparesIntegerAndFloatingPointNodesByValue() {
        JsonNode document = parse("[{\"n\":1},{\"n\":1.0},{\"n\":10e-1},{\"n\":2}]");

        assertThat(JsonPath.compile("$[?@.n == 1]").query(document, Jackson2Model.INSTANCE)).hasSize(3);
    }

    @Test
    void rejectsNodesThatAreNotJson() {
        ObjectNode document = JsonNodeFactory.instance.objectNode();
        document.putPOJO("pojo", new Object());

        assertThatThrownBy(() -> JsonPath.compile("$[?@ == 1]").query(document, Jackson2Model.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
