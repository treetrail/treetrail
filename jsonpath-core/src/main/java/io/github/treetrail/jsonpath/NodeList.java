package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Located;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The result of a query: the selected nodes in order (RFC 9535, section 1.1). A node list may
 * contain the same node more than once, for example for {@code $[0,0]}.
 *
 * <p>A node list, like its nodes, keeps the queried document reachable, because the locations link to the
 * nodes' ancestors. The lists that {@link #values()} and {@link #paths()} return do not.
 *
 * @param <N> the node type of the object model
 */
public final class NodeList<N extends @Nullable Object> extends AbstractList<Node<N>> {

    // The selected nodes as the evaluator found them; Node objects are created only when asked for.
    private final List<Located> nodes;

    NodeList(List<Located> nodes) {
        this.nodes = nodes;
    }

    @Override
    public Node<N> get(int index) {
        return of(nodes.get(index));
    }

    /**
     * Returns the node for a node the evaluator selected in a document of type N. The value is null only for
     * models whose N includes null, which NullAway cannot tell from the cast.
     */
    @SuppressWarnings({"unchecked", "NullAway"})
    static <N extends @Nullable Object> Node<N> of(Located node) {
        return new Node<>((N) node.value(), node);
    }

    @Override
    public int size() {
        return nodes.size();
    }

    /** Returns the selected values in order, in a list that does not refer to the rest of the document. */
    public List<N> values() {
        @Nullable Object[] values = new Object[nodes.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = nodes.get(i).value();
        }
        @SuppressWarnings("unchecked")
        List<N> list = (List<N>) Arrays.asList(values);
        return Collections.unmodifiableList(list);
    }

    /** Returns the normalized paths of the selected nodes in order. */
    public List<String> paths() {
        String[] paths = new String[nodes.size()];
        // A node below an earlier selected node, as in $..*, continues from that node's path instead of
        // building its own from the root; deep documents would otherwise take time quadratic in their depth.
        IdentityHashMap<Located, String> known = new IdentityHashMap<>();
        for (int i = 0; i < paths.length; i++) {
            Located node = nodes.get(i);
            if (node.depth() < Located.REUSE_DEPTH) {
                paths[i] = node.normalizedPath();
            } else {
                String path = node.normalizedPath(known::get);
                known.put(node, path);
                paths[i] = path;
            }
        }
        return Collections.unmodifiableList(Arrays.asList(paths));
    }

    /**
     * Returns the locations of the selected nodes in order, each once: a node that a query selects more than once,
     * as {@code $[0,0]} does, appears at its first place only. Use it to change a document at the selected nodes,
     * where changing a node twice would be wrong; {@link NormalizedPath#compareTo} sorts the paths in document
     * order.
     */
    public List<NormalizedPath> distinctPaths() {
        LinkedHashSet<NormalizedPath> paths = new LinkedHashSet<>();
        for (Located node : nodes) {
            paths.add(node.toNormalizedPath());
        }
        return List.copyOf(paths);
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
        return nodes.isEmpty() ? Optional.empty() : Optional.of(get(0));
    }
}
