package io.github.treetrail.jsonpath;

import java.util.Map;

/**
 * Writes plain Java objects as JSON text, for tests that hand documents to other parsers.
 */
final class TestJson {

    private TestJson() {}

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb) {
        if (value == null || value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof String string) {
            sb.append('"');
            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\').append(c);
                } else if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
            sb.append('"');
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else {
            sb.append('[');
            boolean first = true;
            for (Object element : (Iterable<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(element, sb);
            }
            sb.append(']');
        }
    }
}
