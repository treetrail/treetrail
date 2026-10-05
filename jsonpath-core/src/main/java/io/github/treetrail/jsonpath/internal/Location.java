package io.github.treetrail.jsonpath.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Location of a node in a document, kept as a parent-linked list so that extending a path is O(1).
 * Each step is either a member name ({@link String}) or an array index ({@link Integer}).
 */
public final class Location {

    public static final Location ROOT = new Location(null, null);

    private final Location parent;
    private final Object step;

    private Location(Location parent, Object step) {
        this.parent = parent;
        this.step = step;
    }

    public Location child(String name) {
        return new Location(this, name);
    }

    public Location child(int index) {
        return new Location(this, index);
    }

    /** Returns the steps from the root, each a {@link String} name or an {@link Integer} index. */
    public List<Object> steps() {
        List<Object> steps = new ArrayList<>();
        for (Location l = this; l.parent != null; l = l.parent) {
            steps.add(l.step);
        }
        Collections.reverse(steps);
        return Collections.unmodifiableList(steps);
    }

    /** Returns the normalized path (RFC 9535, section 2.7), for example {@code $['store']['book'][0]}. */
    public String normalizedPath() {
        StringBuilder sb = new StringBuilder("$");
        for (Object step : steps()) {
            if (step instanceof Integer) {
                sb.append('[').append(step).append(']');
            } else {
                sb.append("['");
                appendEscaped(sb, (String) step);
                sb.append("']");
            }
        }
        return sb.toString();
    }

    private static void appendEscaped(StringBuilder sb, String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            switch (c) {
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
    }

    @Override
    public String toString() {
        return normalizedPath();
    }
}
