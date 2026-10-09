package io.github.treetrail.jsonpath.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import java.math.BigDecimal;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link JsonModel} for Gson trees.
 *
 * <pre>{@code
 * JsonElement document = JsonParser.parseString(json);
 * NodeList<JsonElement> nodes = JsonPath.compile("$.store.book[*].title").query(document, GsonModel.INSTANCE);
 * }</pre>
 *
 * <p>JSON {@code null} is {@link com.google.gson.JsonNull}; a Java {@code null} is treated the same way.
 */
public final class GsonModel implements JsonModel<JsonElement> {

    /** The shared instance. The model is stateless. */
    public static final GsonModel INSTANCE = new GsonModel();

    private GsonModel() {
    }

    @Override
    public JsonKind kind(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return JsonKind.NULL;
        }
        if (value.isJsonObject()) {
            return JsonKind.OBJECT;
        }
        if (value.isJsonArray()) {
            return JsonKind.ARRAY;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            return JsonKind.STRING;
        }
        if (primitive.isNumber()) {
            return JsonKind.NUMBER;
        }
        return JsonKind.BOOLEAN;
    }

    @Override
    public Iterable<String> memberNames(JsonElement object) {
        return object.getAsJsonObject().keySet();
    }

    @Override
    public Iterable<Map.Entry<String, JsonElement>> members(JsonElement object) {
        return object.getAsJsonObject().entrySet();
    }

    @Override
    public JsonElement findMember(JsonElement object, String name) {
        return object.getAsJsonObject().get(name);
    }

    @Override
    public boolean hasMember(JsonElement object, String name) {
        return object.getAsJsonObject().has(name);
    }

    @Override
    public JsonElement member(JsonElement object, String name) {
        return object.getAsJsonObject().get(name);
    }

    @Override
    public int memberCount(JsonElement object) {
        return object.getAsJsonObject().size();
    }

    @Override
    public int size(JsonElement array) {
        return array.getAsJsonArray().size();
    }

    @Override
    public JsonElement element(JsonElement array, int index) {
        return array.getAsJsonArray().get(index);
    }

    @Override
    public String stringValue(JsonElement value) {
        return value.getAsString();
    }

    @Override
    public @Nullable BigDecimal numberValue(JsonElement value) {
        Number number = value.getAsNumber();
        // Lenient parsing and new JsonPrimitive(Double.NaN) produce numbers that are not finite.
        if ((number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue())) {
            return null;
        }
        return value.getAsBigDecimal();
    }

    @Override
    public boolean isLong(JsonElement number) {
        Number n = number.getAsNumber();
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            return true;
        }
        if (n instanceof Double || n instanceof Float || n instanceof BigDecimal) {
            return false;
        }
        // Parsed numbers keep their text (LazilyParsedNumber): an optional minus and up to 18 digits fit.
        String text = n.toString();
        int start = text.startsWith("-") ? 1 : 0;
        if (text.length() == start || text.length() - start > 18) {
            return false;
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public long longValue(JsonElement number) {
        Number n = number.getAsNumber();
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte
                ? n.longValue()
                : Long.parseLong(n.toString());
    }

    @Override
    public boolean booleanValue(JsonElement value) {
        return value.getAsBoolean();
    }
}
