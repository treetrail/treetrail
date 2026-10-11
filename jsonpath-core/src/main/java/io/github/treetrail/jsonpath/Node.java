package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Located;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A node selected by a query: a value together with its location in the document.
 *
 * <p>The location links to the node's ancestors in the document, so a node keeps the whole document it was
 * selected from reachable. To keep only the values of a query on a large document, use
 * {@link NodeList#values()}.
 *
 * @param <N> the node type of the object model
 */
public final class Node<N extends @Nullable Object> {

    private final N value;
    private final Located location;
    // Computed on first use; a race only computes the same path twice.
    private @Nullable String path;
    private @Nullable NormalizedPath normalizedPath;

    Node(N value, Located location) {
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

    /** Returns the location of the node as a {@link NormalizedPath}. */
    public NormalizedPath normalizedPath() {
        NormalizedPath path = normalizedPath;
        if (path == null) {
            path = location.toNormalizedPath();
            normalizedPath = path;
        }
        return path;
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
