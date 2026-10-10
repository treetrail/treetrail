package io.github.treetrail.jsonpath.testkit;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Writes plain Java values as {@link io.github.treetrail.jsonpath.JavaObjectModel#parse} reads them back. */
final class JsonText {

    private JsonText() {}

    static String write(@Nullable Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(@Nullable Object value, StringBuilder sb) {
        if (value == null || value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof String string) {
            sb.append('"');
            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\').append(c);
                } else if (c < 0x20) {
                    sb.append("\\u00").append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 0xf, 16));
                } else {
                    sb.append(c);
                }
            }
            sb.append('"');
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            String separator = "";
            for (Map.Entry<?, ?> member : map.entrySet()) {
                sb.append(separator);
                write(member.getKey(), sb);
                sb.append(':');
                write(member.getValue(), sb);
                separator = ",";
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            String separator = "";
            for (Object element : list) {
                sb.append(separator);
                write(element, sb);
                separator = ",";
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException(
                    "Not a JSON value: " + value.getClass().getName());
        }
    }
}
