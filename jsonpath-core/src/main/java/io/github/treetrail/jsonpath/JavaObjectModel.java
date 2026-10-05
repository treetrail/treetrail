package io.github.treetrail.jsonpath;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * {@link JsonModel} for plain Java objects: {@link Map} with {@link String} keys for objects,
 * {@link List} for arrays, {@link String}, {@link Number}, {@link Boolean} and {@code null}.
 *
 * <p>This is the model most JSON libraries produce when asked for "untyped" output, for example
 * Jackson's {@code ObjectMapper.readValue(json, Object.class)}.
 */
public final class JavaObjectModel implements JsonModel<Object> {

    /** The shared instance. The model is stateless. */
    public static final JavaObjectModel INSTANCE = new JavaObjectModel();

    private JavaObjectModel() {
    }

    @Override
    public JsonKind kind(Object value) {
        if (value == null) {
            return JsonKind.NULL;
        }
        if (value instanceof Map) {
            return JsonKind.OBJECT;
        }
        if (value instanceof List) {
            return JsonKind.ARRAY;
        }
        if (value instanceof String) {
            return JsonKind.STRING;
        }
        if (value instanceof Number) {
            return JsonKind.NUMBER;
        }
        if (value instanceof Boolean) {
            return JsonKind.BOOLEAN;
        }
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName());
    }

    @Override
    public Iterable<String> memberNames(Object object) {
        @SuppressWarnings("unchecked")
        Map<String, ?> map = (Map<String, ?>) object;
        return map.keySet();
    }

    @Override
    public boolean hasMember(Object object, String name) {
        return ((Map<?, ?>) object).containsKey(name);
    }

    @Override
    public Object member(Object object, String name) {
        return ((Map<?, ?>) object).get(name);
    }

    @Override
    public int memberCount(Object object) {
        return ((Map<?, ?>) object).size();
    }

    @Override
    public int size(Object array) {
        return ((List<?>) array).size();
    }

    @Override
    public Object element(Object array, int index) {
        return ((List<?>) array).get(index);
    }

    @Override
    public String stringValue(Object value) {
        return (String) value;
    }

    @Override
    public BigDecimal numberValue(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("Not a JSON number: " + value);
            }
            // Float.toString keeps the short decimal form (0.1f -> "0.1").
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(value.toString());
    }

    @Override
    public boolean booleanValue(Object value) {
        return (Boolean) value;
    }
}
