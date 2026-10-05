package io.github.treetrail.jsonpath.jackson3;

import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;

/**
 * {@link JsonModel} for Jackson 3 trees.
 *
 * <pre>{@code
 * JsonNode document = objectMapper.readTree(json);
 * NodeList<JsonNode> nodes = JsonPath.compile("$.store.book[*].title").query(document, Jackson3Model.INSTANCE);
 * }</pre>
 *
 * <p>Binary, POJO and missing nodes are not JSON values and are rejected.
 */
public final class Jackson3Model implements JsonModel<JsonNode> {

    /** The shared instance. The model is stateless. */
    public static final Jackson3Model INSTANCE = new Jackson3Model();

    private Jackson3Model() {
    }

    @Override
    public JsonKind kind(JsonNode value) {
        switch (value.getNodeType()) {
            case OBJECT:
                return JsonKind.OBJECT;
            case ARRAY:
                return JsonKind.ARRAY;
            case STRING:
                return JsonKind.STRING;
            case NUMBER:
                return JsonKind.NUMBER;
            case BOOLEAN:
                return JsonKind.BOOLEAN;
            case NULL:
                return JsonKind.NULL;
            default:
                throw new IllegalArgumentException("Not a JSON value: " + value.getNodeType());
        }
    }

    @Override
    public Iterable<String> memberNames(JsonNode object) {
        return object.propertyNames();
    }

    @Override
    public boolean hasMember(JsonNode object, String name) {
        return object.has(name);
    }

    @Override
    public JsonNode member(JsonNode object, String name) {
        return object.get(name);
    }

    @Override
    public int memberCount(JsonNode object) {
        return object.size();
    }

    @Override
    public int size(JsonNode array) {
        return array.size();
    }

    @Override
    public JsonNode element(JsonNode array, int index) {
        return array.get(index);
    }

    @Override
    public String stringValue(JsonNode value) {
        return value.stringValue();
    }

    @Override
    public BigDecimal numberValue(JsonNode value) {
        return value.decimalValue();
    }

    @Override
    public boolean booleanValue(JsonNode value) {
        return value.booleanValue();
    }
}
