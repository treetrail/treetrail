package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Location;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A node selected by a query: a value together with its location in the document.
 *
 * @param <N> the node type of the object model
 */
public final class Node<N extends @Nullable Object> {

    private final N value;
    private final Location location;
    // Computed on first use; a race only computes the same path twice.
    private @Nullable String path;
    private @Nullable NormalizedPath normalizedPath;

    Node(N value, Location location) {
        this.value = value;
        this.location = location;
    }

    /** Returns the selected value. */
    public N value() {
        return value;
    }

    /**
     * Returns the normalized path of the node (RFC 9535, section 2.7), for example
     * {@code $['store']['book'][0]}.
     */
    public String path() {
        String p = path;
        if (p == null) {
            p = location.normalizedPath();
            path = p;
        }
        return p;
    }

    /** Returns the path, continuing from the path of an ancestor that {@code known} has one for. */
    String path(Function<Location, @Nullable String> known) {
        String p = path;
        if (p == null) {
            p = location.normalizedPath(known);
            path = p;
        }
        return p;
    }

    Location internalLocation() {
        return location;
    }

    /** Returns the location of the node as a {@link NormalizedPath}. */
    public NormalizedPath normalizedPath() {
        NormalizedPath path = normalizedPath;
        if (path == null) {
            path = location.toNormalizedPath();
            normalizedPath = path;
        }
        return path;
    }

    /** Returns the location as steps from the root: {@link String} member names and {@link Integer} indices. */
    public List<Object> location() {
        return location.steps();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof Node)) {
            return false;
        }
        Node<?> other = (Node<?>) o;
        return Objects.equals(value, other.value) && path().equals(other.path());
    }

    @Override
    public int hashCode() {
        // The path alone: equal nodes have equal paths, and hashing a deep value would be costly.
        return path().hashCode();
    }

    @Override
    public String toString() {
        return path() + " = " + value;
    }
}
