package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.NormalizedPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Location of a node in a document, kept as a parent-linked list so that extending a path is O(1).
 * Each step is either a member name ({@link String}) or an array index ({@link Integer}).
 */
public final class Location {

    public static final Location ROOT = new Location(null, null);

    /**
     * Depth from which reusing the path of an ancestor pays off; shallower paths are cheaper to build from
     * the root than to look up.
     */
    public static final int REUSE_DEPTH = 8;

    private final @Nullable Location parent;
    private final @Nullable Object step;
    private final int depth;

    private Location(@Nullable Location parent, @Nullable Object step) {
        this.parent = parent;
        this.step = step;
        this.depth = parent == null ? 0 : parent.depth + 1;
    }

    /** The last step; only the root has none. */
    private Object step() {
        return Objects.requireNonNull(step);
    }

    /** Returns the number of steps from the root; the root has depth 0. */
    public int depth() {
        return depth;
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

    /** Returns the location as a normalized path (RFC 9535, section 2.7). */
    public NormalizedPath toNormalizedPath() {
        NormalizedPath.Step[] steps = new NormalizedPath.Step[depth];
        int i = depth;
        for (Location l = this; l.parent != null; l = l.parent) {
            Object step = l.step();
            steps[--i] = step instanceof Integer index
                    ? new NormalizedPath.Index(index)
                    : new NormalizedPath.Name((String) step);
        }
        return NormalizedPath.of(Arrays.asList(steps));
    }

    /** Returns the normalized path as a string, for example {@code $['store']['book'][0]}. */
    public String normalizedPath() {
        return normalizedPath(l -> null);
    }

    /**
     * Returns the normalized path as a string, continuing from the path of the nearest location at least
     * {@link #REUSE_DEPTH} deep that {@code known} has one for. Paths of nodes below other selected nodes,
     * as in {@code $..*}, then cost only their own steps.
     */
    public String normalizedPath(Function<Location, @Nullable String> known) {
        String prefix = "$";
        int count = 0;
        for (Location l = this; l.parent != null; l = l.parent) {
            String path = l.depth >= REUSE_DEPTH ? known.apply(l) : null;
            if (path != null) {
                prefix = path;
                break;
            }
            count++;
        }
        Object[] steps = new Object[count];
        Location l = this;
        for (int i = count - 1; i >= 0; i--) {
            steps[i] = l.step();
            l = Objects.requireNonNull(l.parent);
        }
        StringBuilder sb = new StringBuilder(prefix.length() + 8 * count).append(prefix);
        for (Object step : steps) {
            appendStep(sb, step);
        }
        return sb.toString();
    }

    /** Appends a step of a normalized path: {@code ['name']} or {@code [0]}. */
    public static void appendStep(StringBuilder sb, Object step) {
        if (step instanceof Integer index) {
            sb.append('[').append((int) index).append(']');
        } else {
            sb.append("['");
            appendEscaped(sb, (String) step);
            sb.append("']");
        }
    }

    /** Escapes a member name as RFC 9535, section 2.7 prescribes. */
    private static void appendEscaped(StringBuilder sb, String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            switch (c) {
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\'' -> sb.append("\\'");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u00").append(c < 0x10 ? "0" : "1").append(Character.forDigit(c & 0xf, 16));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return normalizedPath();
    }
}
