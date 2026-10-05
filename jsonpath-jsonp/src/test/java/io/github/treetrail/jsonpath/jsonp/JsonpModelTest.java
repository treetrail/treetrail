package io.github.treetrail.jsonpath.jsonp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.NodeList;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class JsonpModelTest {

    @TestFactory
    Stream<DynamicTest> complianceTestSuite() {
        return ComplianceSuite.tests(JsonpModel.INSTANCE, JsonpModelTest::parse, JsonValue::toString);
    }

    @Test
    void returnsTheOriginalValues() {
        JsonValue document = parse("{\"store\":{\"book\":[{\"title\":\"A\",\"price\":8.95},{\"title\":\"B\",\"price\":12}]}}");

        NodeList<JsonValue> nodes = JsonPath.compile("$.store.book[?@.price < 10]").query(document, JsonpModel.INSTANCE);

        assertThat(nodes.values()).containsExactly(
                document.asJsonObject().getJsonObject("store").getJsonArray("book").get(0));
    }

    @Test
    void comparesNumbersByValue() {
        JsonValue document = parse("[{\"n\":1},{\"n\":1.0},{\"n\":10e-1},{\"n\":2}]");

        assertThat(JsonPath.compile("$[?@.n == 1]").query(document, JsonpModel.INSTANCE)).hasSize(3);
    }

    @Test
    void distinguishesJsonNullFromMissingMembers() {
        JsonValue document = parse("[{\"a\":null},{}]");

        assertThat(JsonPath.compile("$[?@.a == null]").query(document, JsonpModel.INSTANCE)).hasSize(1);
    }

    private static JsonValue parse(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readValue();
        }
    }
}
