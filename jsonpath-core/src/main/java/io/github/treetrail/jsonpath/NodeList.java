package io.github.treetrail.jsonpath;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The result of a query: the selected nodes in order (RFC 9535, section 1.1). A node list may
 * contain the same node more than once, for example for {@code $[0,0]}.
 *
 * @param <N> the node type of the object model
 */
public final class NodeList<N> extends AbstractList<Node<N>> {

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
        for (Node<N> node : nodes) {
            paths.add(node.path());
        }
        return Collections.unmodifiableList(paths);
    }

    /**
     * Returns the only selected value, or empty if no node was selected.
     *
     * @throws IllegalStateException if more than one node was selected
     */
    public Optional<N> single() {
        if (nodes.size() > 1) {
            throw new IllegalStateException("Expected at most one node but got " + nodes.size());
        }
        return nodes.isEmpty() ? Optional.empty() : Optional.ofNullable(nodes.get(0).value());
    }
}
