package com.christophsens.jsonpath.gson;

import com.christophsens.jsonpath.JsonKind;
import com.christophsens.jsonpath.JsonModel;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;

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
    public BigDecimal numberValue(JsonElement value) {
        return value.getAsBigDecimal();
    }

    @Override
    public boolean booleanValue(JsonElement value) {
        return value.getAsBoolean();
    }
}
