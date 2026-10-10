package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Location;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The result of a query: the selected nodes in order (RFC 9535, section 1.1). A node list may
 * contain the same node more than once, for example for {@code $[0,0]}.
 *
 * @param <N> the node type of the object model
 */
public final class NodeList<N extends @Nullable Object> extends AbstractList<Node<N>> {

    private final List<Node<N>> nodes;

    NodeList(List<Node<N>> nodes) {
        this.nodes = Collections.unmodifiableList(nodes);
    }

    @Override
    public Node<N> get(int index) {
        return nodes.get(index);
    }

    @Override
    public int size() {
        return nodes.size();
    }

    /** Returns the selected values in order. */
    public List<N> values() {
        List<N> values = new ArrayList<>(nodes.size());
        for (Node<N> node : nodes) {
            values.add(node.value());
        }
        return Collections.unmodifiableList(values);
    }

    /** Returns the normalized paths of the selected nodes in order. */
    public List<String> paths() {
        List<String> paths = new ArrayList<>(nodes.size());
        // A node below an earlier selected node, as in $..*, continues from that node's path instead of
        // building its own from the root; deep documents would otherwise take time quadratic in their depth.
        IdentityHashMap<Location, String> known = new IdentityHashMap<>();
        for (Node<N> node : nodes) {
            Location location = node.internalLocation();
            if (location.depth() < Location.REUSE_DEPTH) {
                paths.add(node.path());
            } else {
                String path = node.path(known::get);
                known.put(location, path);
                paths.add(path);
            }
        }
        return Collections.unmodifiableList(paths);
    }

    /**
     * Returns the only selected node, or empty if no node was selected.
     *
     * <p>A node is returned even if its value is JSON {@code null}, which plain Java objects
     * ({@link JavaObjectModel}) represent as {@code null}. That distinguishes {@code {"a": null}} from a
     * document without {@code a}. Note that {@code single().map(Node::value)} loses this distinction again,
     * because {@link Optional#map} turns a {@code null} result into an empty optional.
     *
     * @throws IllegalStateException if more than one node was selected
     */
    public Optional<Node<N>> single() {
        if (nodes.size() > 1) {
            throw new IllegalStateException("Expected at most one node but got " + nodes.size());
        }
        return first();
    }

    /** Returns the first selected node, or empty if no node was selected; see {@link #single()} on null values. */
    public Optional<Node<N>> first() {
        return nodes.isEmpty() ? Optional.empty() : Optional.of(nodes.get(0));
    }
}
