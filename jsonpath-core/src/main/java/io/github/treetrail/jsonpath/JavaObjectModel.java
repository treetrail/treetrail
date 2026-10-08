package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.JsonReader;
import io.github.treetrail.jsonpath.internal.Values;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link JsonModel} for plain Java objects: {@link Map} with {@link String} keys for objects,
 * {@link List} and Java arrays (including primitive arrays) for arrays, {@link String} and
 * {@link Character} for strings, {@link Number}, {@link Boolean} and {@code null}.
 *
 * <p>Other collections such as {@link java.util.Set} are rejected because their elements have no
 * index; copy them into a {@code List}. A map that contains itself, directly or indirectly, is
 * infinitely deep; queries stop at {@link EvaluationLimits#maxDepth()}.
 *
 * <p>This is the model most JSON libraries produce when asked for "untyped" output, for example
 * Jackson's {@code ObjectMapper.readValue(json, Object.class)}.
 */
public final class JavaObjectModel implements JsonModel<Object> {

    /** The shared instance. The model is stateless. */
    public static final JavaObjectModel INSTANCE = new JavaObjectModel();

    private JavaObjectModel() {
    }

    /**
     * Returns whether two plain Java values are equal as JSON values, with the semantics of {@code ==} in
     * filters: numbers by value ({@code 10}, {@code 10L}, {@code 10.0} and {@code new BigDecimal("10.00")}
     * are equal), strings by content, lists element by element and maps member by member regardless of
     * order. Useful to compare query results with expected values in tests.
     *
     * @throws JsonPathLimitExceededException if the values are nested more than 1,000 levels deep
     * @throws IllegalArgumentException if a value is not a JSON value of this model
     */
    public static boolean jsonEquals(Object a, Object b) {
        return Values.jsonEquals(INSTANCE, a, INSTANCE, b, EvaluationLimits.DEFAULT.maxDepth());
    }

    /**
     * Parses JSON text (RFC 8259) into plain Java objects for this model: {@link java.util.LinkedHashMap}
     * for objects (members in document order), {@link java.util.ArrayList} for arrays, {@link String},
     * {@link Boolean} and {@code null}. Integers become {@link Integer}, {@link Long} or {@link BigInteger},
     * whichever is the smallest that fits; numbers with a fraction or exponent become {@link BigDecimal}, so
     * no precision is lost.
     *
     * <p>The parser is strict: besides RFC 8259 it rejects duplicate member names and unpaired surrogates
     * (I-JSON, RFC 7493), and nesting deeper than 1,000 levels. It exists so that a JSON string can be
     * queried without a JSON library, for example a response body in a test; applications that already use
     * Jackson, Gson or JSON-P can query their trees directly through the adapters instead.
     *
     * @throws InvalidJsonException if {@code json} is not valid JSON text
     * @throws NullPointerException if {@code json} is null
     */
    public static Object parse(String json) {
        return JsonReader.parse(Objects.requireNonNull(json, "json"));
    }

    @Override
    public JsonKind kind(Object value) {
        if (value == null) {
            return JsonKind.NULL;
        }
        if (value instanceof Map) {
            return JsonKind.OBJECT;
        }
        if (value instanceof List || value.getClass().isArray()) {
            return JsonKind.ARRAY;
        }
        if (value instanceof String || value instanceof Character) {
            return JsonKind.STRING;
        }
        if (value instanceof Number) {
            return JsonKind.NUMBER;
        }
        if (value instanceof Boolean) {
            return JsonKind.BOOLEAN;
        }
        String hint = value instanceof Collection
                ? ". Collections other than List have no element order; copy them into a List"
                : adapterHint(value.getClass());
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName() + hint);
    }

    /** Tree types of JSON libraries that have an adapter, by fully qualified name of a supertype. */
    private static final Map<String, String> ADAPTERS = Map.of(
            "com.fasterxml.jackson.databind.JsonNode", "Jackson 2 trees, use Jackson2Model.INSTANCE from jsonpath-jackson2",
            "tools.jackson.databind.JsonNode", "Jackson 3 trees, use Jackson3Model.INSTANCE from jsonpath-jackson3",
            "com.google.gson.JsonElement", "Gson trees, use GsonModel.INSTANCE from jsonpath-gson",
            "jakarta.json.JsonValue", "JSON-P values, use JsonpModel.INSTANCE from jsonpath-jsonp");

    /** Suggests the adapter when a tree of a known JSON library was passed as plain Java objects. */
    private static String adapterHint(Class<?> type) {
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.push(type);
        while (!pending.isEmpty()) {
            Class<?> c = pending.pop();
            String adapter = ADAPTERS.get(c.getName());
            if (adapter != null) {
                return ". For " + adapter + ": query(document, model)";
            }
            if (c.getSuperclass() != null) {
                pending.push(c.getSuperclass());
            }
            for (Class<?> i : c.getInterfaces()) {
                pending.push(i);
            }
        }
        return "";
    }

    @Override
    public Iterable<String> memberNames(Object object) {
        Set<?> keys = ((Map<?, ?>) object).keySet();
        // Checks each key while iterating, so that maps with other keys fail with a clear message.
        return () -> new Iterator<String>() {
            private final Iterator<?> it = keys.iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public String next() {
                return checkKey(it.next());
            }
        };
    }

    @Override
    public Iterable<Map.Entry<String, Object>> members(Object object) {
        Set<? extends Map.Entry<?, ?>> entries = ((Map<?, ?>) object).entrySet();
        return () -> new Iterator<Map.Entry<String, Object>>() {
            private final Iterator<? extends Map.Entry<?, ?>> it = entries.iterator();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Map.Entry<String, Object> next() {
                Map.Entry<?, ?> entry = it.next();
                checkKey(entry.getKey());
                @SuppressWarnings("unchecked")
                Map.Entry<String, Object> member = (Map.Entry<String, Object>) entry;
                return member;
            }
        };
    }

    private static String checkKey(Object key) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Map keys must be strings to be JSON object members, found "
                    + (key == null ? "null" : key.getClass().getName()));
        }
        return (String) key;
    }

    @Override
    public Object findMember(Object object, String name) {
        // Null for a missing member and for JSON null; the caller tells them apart with hasMember.
        return ((Map<?, ?>) object).get(name);
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
        return array instanceof List ? ((List<?>) array).size() : Array.getLength(array);
    }

    @Override
    public Object element(Object array, int index) {
        return array instanceof List ? ((List<?>) array).get(index) : Array.get(array, index);
    }

    @Override
    public String stringValue(Object value) {
        return value.toString();
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
                return null;
            }
            // Float.toString keeps the short decimal form (0.1f -> "0.1").
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(value.toString());
    }

    /** Largest absolute value up to which every integer is exactly representable as a {@code double}: 2^53. */
    private static final double EXACT_DOUBLE_LIMIT = 9_007_199_254_740_992.0;

    @Override
    public boolean isLong(Object number) {
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            return true;
        }
        if (number instanceof Double || number instanceof Float) {
            // Integral doubles such as 10.0 have the same value as numberValue() gives them.
            double d = ((Number) number).doubleValue();
            return d == Math.rint(d) && Math.abs(d) < EXACT_DOUBLE_LIMIT;
        }
        if (number instanceof BigDecimal) {
            // Integer literals of queries are BigDecimals with scale 0; 18 digits always fit in a long.
            BigDecimal decimal = (BigDecimal) number;
            return decimal.scale() == 0 && decimal.precision() <= 18;
        }
        return number instanceof BigInteger && ((BigInteger) number).bitLength() < Long.SIZE;
    }

    @Override
    public long longValue(Object number) {
        return ((Number) number).longValue();
    }

    @Override
    public boolean booleanValue(Object value) {
        return (Boolean) value;
    }
}
