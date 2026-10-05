package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Location;
import java.util.List;
import java.util.Objects;

/**
 * A node selected by a query: a value together with its location in the document.
 *
 * @param <N> the node type of the object model
 */
public final class Node<N> {

    private final N value;
    private final Location location;

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
        return location.normalizedPath();
    }

    /** Returns the location as steps from the root: {@link String} member names and {@link Integer} indices. */
    public List<Object> location() {
        return location.steps();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Node)) {
            return false;
        }
        Node<?> other = (Node<?>) o;
        return Objects.equals(value, other.value) && path().equals(other.path());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, path());
    }

    @Override
    public String toString() {
        return path() + " = " + value;
    }
}
