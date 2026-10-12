package io.github.treetrail.openapi.overlay;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.treetrail.jsonpath.JsonPath;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One action of an overlay: a target query and what to do with the nodes it selects.
 *
 * <p>When an action has several modifiers, {@link #remove()} wins over {@link #update()}, and {@link #update()}
 * over {@link #copy()} ({@link #kind()}). An action without modifier changes nothing.
 *
 * @param index the position of the action in the overlay's {@code actions}, from 0
 * @param target the RFC 9535 query that selects the nodes to change
 * @param description the description, or null
 * @param update the value to merge into each selected node, or null if absent; JSON {@code null} is a
 *     {@link com.fasterxml.jackson.databind.node.NullNode}
 * @param copy the RFC 9535 query that selects the single node to merge into each selected node, or null
 * @param remove whether to remove the selected nodes
 * @param reusableAction for an action that references a reusable action (Overlay 1.2), the key of that action in
 *     {@code components.actions}; otherwise null
 * @param extensions the specification extensions ({@code x-...}) of the action
 */
public record OverlayAction(
        int index,
        JsonPath target,
        @Nullable String description,
        @Nullable JsonNode update,
        @Nullable JsonPath copy,
        boolean remove,
        @Nullable String reusableAction,
        Map<String, JsonNode> extensions) {

    /** What an action does. */
    public enum Kind {
        /** Removes the selected nodes. */
        REMOVE,
        /** Merges {@link #update()} into the selected nodes. */
        UPDATE,
        /** Merges the node {@link #copy()} selects into the selected nodes. */
        COPY,
        /** Nothing: the action has no modifier. */
        NONE
    }

    /** Creates an action. */
    public OverlayAction {
        extensions = Map.copyOf(extensions);
    }

    /** Returns what the action does: remove, else update, else copy. */
    public Kind kind() {
        if (remove) {
            return Kind.REMOVE;
        }
        if (update != null) {
            return Kind.UPDATE;
        }
        return copy != null ? Kind.COPY : Kind.NONE;
    }
}
