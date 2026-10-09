package io.github.treetrail.jsonpath.jsonp;

import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@link JsonModel} for Jakarta JSON Processing (JSON-P) trees, with any JSON-P implementation.
 *
 * <pre>{@code
 * JsonValue document = Json.createReader(new StringReader(json)).readValue();
 * NodeList<JsonValue> nodes = JsonPath.compile("$.store.book[*].title").query(document, JsonpModel.INSTANCE);
 * }</pre>
 */
public final class JsonpModel implements JsonModel<JsonValue> {

    /** The shared instance. The model is stateless. */
    public static final JsonpModel INSTANCE = new JsonpModel();

    private JsonpModel() {
    }

    @Override
    public JsonKind kind(JsonValue value) {
        switch (value.getValueType()) {
            case OBJECT:
                return JsonKind.OBJECT;
            case ARRAY:
                return JsonKind.ARRAY;
            case STRING:
                return JsonKind.STRING;
            case NUMBER:
                return JsonKind.NUMBER;
            case TRUE:
            case FALSE:
                return JsonKind.BOOLEAN;
            case NULL:
                return JsonKind.NULL;
            default:
                throw new IllegalArgumentException("Not a JSON value: " + value.getValueType());
        }
    }

    @Override
    public Iterable<String> memberNames(JsonValue object) {
        return object.asJsonObject().keySet();
    }

    @Override
    public Iterable<Map.Entry<String, JsonValue>> members(JsonValue object) {
        return object.asJsonObject().entrySet();
    }

    @Override
    public @Nullable JsonValue findMember(JsonValue object, String name) {
        // Null only for a missing member; JSON null is JsonValue.NULL.
        return object.asJsonObject().get(name);
    }

    // isLong() keeps its default: JsonNumber offers no allocation-free test for "fits in a long".

    @Override
    public boolean hasMember(JsonValue object, String name) {
        return object.asJsonObject().containsKey(name);
    }

    @Override
    public JsonValue member(JsonValue object, String name) {
        return Objects.requireNonNull(object.asJsonObject().get(name), name);
    }

    @Override
    public int memberCount(JsonValue object) {
        return object.asJsonObject().size();
    }

    @Override
    public int size(JsonValue array) {
        return array.asJsonArray().size();
    }

    @Override
    public JsonValue element(JsonValue array, int index) {
        return array.asJsonArray().get(index);
    }

    @Override
    public String stringValue(JsonValue value) {
        return ((JsonString) value).getString();
    }

    @Override
    public BigDecimal numberValue(JsonValue value) {
        return ((JsonNumber) value).bigDecimalValue();
    }

    @Override
    public boolean booleanValue(JsonValue value) {
        return value.getValueType() == JsonValue.ValueType.TRUE;
    }
}
