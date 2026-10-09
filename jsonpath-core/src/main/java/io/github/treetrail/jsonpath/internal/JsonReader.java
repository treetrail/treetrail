package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.InvalidJsonException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict parser for JSON text (RFC 8259) into the plain Java objects of
 * {@link io.github.treetrail.jsonpath.JavaObjectModel}.
 *
 * <p>Besides RFC 8259, it enforces the I-JSON rules (RFC 7493) that queries rely on: member names are
 * unique and strings contain no unpaired surrogates. Nesting is limited to {@link #MAX_DEPTH} levels.
 */
public final class JsonReader {

    /** Maximum nesting of arrays and objects, the default depth limit of queries. */
    static final int MAX_DEPTH = 1_000;

    private final String src;
    private int pos;
    private int depth;

    private JsonReader(String src) {
        this.src = src;
    }

    /**
     * Parses JSON text. Objects become {@link LinkedHashMap}s in document order, arrays
     * {@link ArrayList}s, integers {@link Integer}, {@link Long} or {@link BigInteger} (the smallest that
     * fits), other numbers {@link BigDecimal}.
     *
     * @throws InvalidJsonException if {@code json} is not valid JSON text
     */
    public static Object parse(String json) {
        JsonReader reader = new JsonReader(json);
        reader.skipWhitespace();
        Object value = reader.value();
        reader.skipWhitespace();
        if (reader.pos != json.length()) {
            throw reader.error("Unexpected text after the JSON value");
        }
        return value;
    }

    private Object value() {
        if (pos >= src.length()) {
            throw error("Expected a value");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", true);
            case 'f':
                return literal("false", false);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("Expected a value");
        }
    }

    private Map<String, Object> object() {
        int start = pos;
        enter();
        pos++;
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            depth--;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a member name");
            }
            int nameStart = pos;
            String name = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            if (object.containsKey(name)) {
                throw error("Duplicate member name \"" + name + "\" in the object starting at position " + start,
                        nameStart);
            }
            object.put(name, value());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else if (peek() == '}') {
                pos++;
                depth--;
                return object;
            } else {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> array() {
        enter();
        pos++;
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            depth--;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(value());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else if (peek() == ']') {
                pos++;
                depth--;
                return array;
            } else {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw error("JSON is nested more than " + MAX_DEPTH + " levels deep");
        }
    }

    private String string() {
        int start = pos;
        pos++;
        StringBuilder sb = null;
        int chunk = pos;
        while (true) {
            if (pos >= src.length()) {
                throw error("Unterminated string", start);
            }
            char c = src.charAt(pos);
            if (c == '"') {
                String s = sb == null ? src.substring(chunk, pos) : sb.append(src, chunk, pos).toString();
                pos++;
                return s;
            }
            if (c < 0x20) {
                throw error("Control characters in strings must be escaped");
            }
            if (Character.isHighSurrogate(c)) {
                if (pos + 1 >= src.length() || !Character.isLowSurrogate(src.charAt(pos + 1))) {
                    throw error("Unpaired surrogate");
                }
                pos += 2;
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                throw error("Unpaired surrogate");
            }
            if (c != '\\') {
                pos++;
                continue;
            }
            if (sb == null) {
                sb = new StringBuilder();
            }
            sb.append(src, chunk, pos);
            pos++;
            escape(sb);
            chunk = pos;
        }
    }

    private void escape(StringBuilder sb) {
        if (pos >= src.length()) {
            throw error("Unterminated escape");
        }
        char c = src.charAt(pos++);
        switch (c) {
            case '"':
            case '\\':
            case '/':
                sb.append(c);
                break;
            case 'b':
                sb.append('\b');
                break;
            case 'f':
                sb.append('\f');
                break;
            case 'n':
                sb.append('\n');
                break;
            case 'r':
                sb.append('\r');
                break;
            case 't':
                sb.append('\t');
                break;
            case 'u':
                char unit = hex4();
                if (Character.isHighSurrogate(unit)) {
                    if (!src.startsWith("\\u", pos)) {
                        throw error("Unpaired surrogate");
                    }
                    pos += 2;
                    char low = hex4();
                    if (!Character.isLowSurrogate(low)) {
                        throw error("Unpaired surrogate");
                    }
                    sb.append(unit).append(low);
                } else if (Character.isLowSurrogate(unit)) {
                    throw error("Unpaired surrogate");
                } else {
                    sb.append(unit);
                }
                break;
            default:
                throw error("Invalid escape", pos - 2);
        }
    }

    private char hex4() {
        if (pos + 4 > src.length()) {
            throw error("Expected four hex digits");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char h = src.charAt(pos + i);
            int digit;
            if (h >= '0' && h <= '9') {
                digit = h - '0';
            } else if (h >= 'a' && h <= 'f') {
                digit = h - 'a' + 10;
            } else if (h >= 'A' && h <= 'F') {
                digit = h - 'A' + 10;
            } else {
                throw error("Expected four hex digits");
            }
            value = value * 16 + digit;
        }
        pos += 4;
        return (char) value;
    }

    private Object number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
        } else if (isDigit(peek())) {
            while (isDigit(peek())) {
                pos++;
            }
        } else {
            throw error("Expected a digit");
        }
        boolean integer = true;
        if (peek() == '.') {
            integer = false;
            pos++;
            if (!isDigit(peek())) {
                throw error("Expected a digit after '.'");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            integer = false;
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                throw error("Expected a digit in the exponent");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        String text = src.substring(start, pos);
        if (integer) {
            // At most 18 digits always fit in a long.
            if (text.length() - (text.charAt(0) == '-' ? 1 : 0) <= 18) {
                long value = Long.parseLong(text);
                return value == (int) value ? (Object) (int) value : (Object) value;
            }
            BigInteger value = new BigInteger(text);
            return value.bitLength() < Long.SIZE ? (Object) value.longValue() : value;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw error("Number out of range", start);
        }
    }

    private Object literal(String word, Object value) {
        if (!src.startsWith(word, pos)) {
            throw error("Expected a value");
        }
        pos += word.length();
        return value;
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private int peek() {
        return pos < src.length() ? src.charAt(pos) : -1;
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private InvalidJsonException error(String reason) {
        return error(reason, pos);
    }

    private InvalidJsonException error(String reason, int at) {
        return new InvalidJsonException(reason, at);
    }
}
