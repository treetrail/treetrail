package io.github.treetrail.jsonpath.jackson2;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import java.math.BigDecimal;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link JsonModel} for Jackson 2 trees.
 *
 * <pre>{@code
 * JsonNode document = objectMapper.readTree(json);
 * NodeList<JsonNode> nodes = JsonPath.compile("$.store.book[*].title").query(document, Jackson2Model.INSTANCE);
 * }</pre>
 *
 * <p>Binary, POJO and missing nodes are not JSON values and are rejected.
 */
public final class Jackson2Model implements JsonModel<JsonNode> {

    /** The shared instance. The model is stateless. */
    public static final Jackson2Model INSTANCE = new Jackson2Model();

    private Jackson2Model() {}

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
        return object::fieldNames;
    }

    @Override
    public Iterable<Map.Entry<String, JsonNode>> members(JsonNode object) {
        return object.properties();
    }

    @Override
    public JsonNode findMember(JsonNode object, String name) {
        // Null only for a missing member; JSON null is a NullNode.
        return object.get(name);
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
        return value.textValue();
    }

    @Override
    public @Nullable BigDecimal numberValue(JsonNode value) {
        // Doubles and floats can be NaN or infinite, for example 1e400 parsed as a double.
        if ((value.isDouble() || value.isFloat()) && !Double.isFinite(value.doubleValue())) {
            return null;
        }
        return value.decimalValue();
    }

    @Override
    public boolean isLong(JsonNode number) {
        return number.isIntegralNumber() && number.canConvertToLong();
    }

    @Override
    public long longValue(JsonNode number) {
        return number.longValue();
    }

    @Override
    public boolean booleanValue(JsonNode value) {
        return value.booleanValue();
    }
}
