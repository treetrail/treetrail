package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.NormalizedPath;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A node of the document together with its location. The location is kept as a link to the parent node and
 * the step from there, a member name or an array index, so that a child costs one object and extending a path
 * is O(1); the chain of parents is the path. Path strings are built only when asked for.
 */
public final class Located {

    /**
     * Depth from which reusing the path of an ancestor pays off; shallower paths are cheaper to build from
     * the root than to look up.
     */
    public static final int REUSE_DEPTH = 8;

    private final @Nullable Object value;
    private final @Nullable Located parent;
    /** The member name of the last step, or null if it is an array index or this is the root. */
    private final @Nullable String name;
    /** The array index of the last step, or -1 if it is a member name or this is the root. */
    private final int index;

    private final int depth;

    private Located(@Nullable Object value, @Nullable Located parent, @Nullable String name, int index) {
        this.value = value;
        this.parent = parent;
        this.name = name;
        this.index = index;
        this.depth = parent == null ? 0 : parent.depth + 1;
    }

    /** Returns the root node of a document. */
    public static Located root(@Nullable Object document) {
        return new Located(document, null, null, -1);
    }

    /** Returns the member with the given name and value of this node. */
    public Located child(@Nullable Object value, String name) {
        return new Located(value, this, name, -1);
    }

    /** Returns the element with the given index and value of this node. */
    public Located child(@Nullable Object value, int index) {
        return new Located(value, this, null, index);
    }

    public @Nullable Object value() {
        return value;
    }

    /** Returns the number of steps from the root; the root has depth 0. */
    public int depth() {
        return depth;
    }

    private Located parent() {
        return Objects.requireNonNull(parent);
    }

    /** Returns the location as a normalized path (RFC 9535, section 2.7). */
    public NormalizedPath toNormalizedPath() {
        NormalizedPath.Step[] steps = new NormalizedPath.Step[depth];
        int i = depth;
        for (Located l = this; l.parent != null; l = l.parent) {
            steps[--i] = l.name != null ? new NormalizedPath.Name(l.name) : new NormalizedPath.Index(l.index);
        }
        return NormalizedPath.of(Arrays.asList(steps));
    }

    /** Returns the normalized path as a string, for example {@code $['store']['book'][0]}. */
    public String normalizedPath() {
        return normalizedPath(l -> null);
    }

    /**
     * Returns the normalized path as a string, continuing from the path of the nearest ancestor at least
     * {@link #REUSE_DEPTH} deep that {@code known} has one for. Paths of nodes below other selected nodes,
     * as in {@code $..*}, then cost only their own steps.
     */
    public String normalizedPath(Function<Located, @Nullable String> known) {
        String prefix = "$";
        int count = 0;
        for (Located l = this; l.parent != null; l = l.parent) {
            String path = l.depth >= REUSE_DEPTH ? known.apply(l) : null;
            if (path != null) {
                prefix = path;
                break;
            }
            count++;
        }
        Located[] steps = new Located[count];
        Located l = this;
        for (int i = count - 1; i >= 0; i--) {
            steps[i] = l;
            l = l.parent();
        }
        StringBuilder sb = new StringBuilder(prefix.length() + 8 * count).append(prefix);
        for (Located step : steps) {
            if (step.name != null) {
                appendName(sb, step.name);
            } else {
                appendIndex(sb, step.index);
            }
        }
        return sb.toString();
    }

    /** Appends an index step of a normalized path: {@code [0]}. */
    public static void appendIndex(StringBuilder sb, int index) {
        sb.append('[').append(index).append(']');
    }

    /** Appends a name step of a normalized path, {@code ['name']}, escaped as RFC 9535, section 2.7 prescribes. */
    public static void appendName(StringBuilder sb, String name) {
        sb.append("['");
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
        sb.append("']");
    }
}
