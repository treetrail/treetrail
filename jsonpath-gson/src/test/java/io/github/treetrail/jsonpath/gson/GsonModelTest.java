package io.github.treetrail.jsonpath.gson;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.NodeList;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class GsonModelTest {

    @TestFactory
    Stream<DynamicTest> complianceTestSuite() {
        return ComplianceSuite.tests(GsonModel.INSTANCE, JsonParser::parseString, JsonElement::toString);
    }

    @Test
    void returnsTheOriginalElements() {
        JsonElement document = JsonParser.parseString(
                "{\"store\":{\"book\":[{\"title\":\"A\",\"price\":8.95},{\"title\":\"B\",\"price\":12}]}}");

        NodeList<JsonElement> nodes = JsonPath.compile("$.store.book[?@.price < 10]").query(document, GsonModel.INSTANCE);

        assertThat(nodes.values()).containsExactly(
                document.getAsJsonObject().getAsJsonObject("store").getAsJsonArray("book").get(0));
    }

    @Test
    void comparesNumbersByValue() {
        JsonElement document = JsonParser.parseString("[{\"n\":1},{\"n\":1.0},{\"n\":10e-1},{\"n\":2}]");

        assertThat(JsonPath.compile("$[?@.n == 1]").query(document, GsonModel.INSTANCE)).hasSize(3);
    }

    @Test
    void treatsJavaNullAsJsonNull() {
        // Gson's tree API accepts Java null and stores JsonNull, but hand-built trees may contain null.
        JsonObject document = new JsonObject();
        JsonArray array = new JsonArray();
        array.add((JsonElement) null);
        document.add("a", array);

        assertThat(JsonPath.compile("$.a[?@ == null]").query(document, GsonModel.INSTANCE)).hasSize(1);
    }

    @Test
    void treatsNumbersThatAreNotFiniteAsIncomparable() {
        JsonArray document = new JsonArray();
        document.add(Double.NaN);
        document.add(Double.POSITIVE_INFINITY);
        document.add(2);

        assertThat(JsonPath.compile("$[?@ > 1]").query(document, GsonModel.INSTANCE).values())
                .containsExactly(document.get(2));
        assertThat(JsonPath.compile("$[?@ != 2]").query(document, GsonModel.INSTANCE)).hasSize(2);
    }

    @Test
    void comparesParsedNumbersOfEveryLength() {
        JsonElement document = JsonParser.parseString(
                "[-123, 123456789012345678, 1234567890123456789, 12345678901234567890, 1e2, 100, 1.5]");

        assertThat(JsonPath.compile("$[?@ == 100]").query(document, GsonModel.INSTANCE)).hasSize(2);
        assertThat(JsonPath.compile("$[?@ > 123456789012345678]").query(document, GsonModel.INSTANCE)).hasSize(2);
        assertThat(JsonPath.compile("$[?@ < 0]").query(document, GsonModel.INSTANCE)).hasSize(1);
    }
}
