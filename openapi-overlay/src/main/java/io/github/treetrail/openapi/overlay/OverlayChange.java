package io.github.treetrail.openapi.overlay;

import io.github.treetrail.jsonpath.NormalizedPath;
import java.util.Objects;

/**
 * A change an action made at one of the nodes its target selected, for explaining what an overlay did.
 *
 * @param actionIndex the position of the action in the overlay's {@code actions}, from 0
 * @param path the location of the changed node, as it was when the action ran
 * @param kind what the action did there
 */
public record OverlayChange(int actionIndex, NormalizedPath path, Kind kind) {

    /** What an action did at a node. */
    public enum Kind {
        /** Removed the node from its parent. */
        REMOVED,
        /** Merged an object into the object node. */
        MERGED,
        /** Appended the elements of an array to the array node. */
        CONCATENATED,
        /** Appended a value as one element to the array node. */
        APPENDED,
        /** Replaced the node: a primitive by a primitive, or any value in lenient mode. */
        REPLACED
    }

    /** Creates a change. */
    public OverlayChange {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
    }

    /** Returns the change as one line, for example {@code /actions/0: MERGED $['info']}. */
    @Override
    public String toString() {
        return "/actions/" + actionIndex + ": " + kind + " " + path;
    }
}
