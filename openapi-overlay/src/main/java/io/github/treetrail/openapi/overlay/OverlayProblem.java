package io.github.treetrail.openapi.overlay;

import java.util.Objects;

/**
 * Something that makes an overlay document invalid.
 *
 * @param location the JSON Pointer of the field in the overlay, for example {@code /actions/2/target}; empty for
 *     the root
 * @param message what is wrong
 */
public record OverlayProblem(String location, String message) {

    /** Creates a problem. */
    public OverlayProblem {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    /** Returns the problem as one line, for example {@code /actions/2/target: must be a string}. */
    @Override
    public String toString() {
        return (location.isEmpty() ? "Overlay" : location) + ": " + message;
    }
}
