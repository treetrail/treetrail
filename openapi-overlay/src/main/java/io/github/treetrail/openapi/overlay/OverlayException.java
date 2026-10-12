package io.github.treetrail.openapi.overlay;

import java.io.Serial;

/**
 * An action of an overlay cannot be applied to a document, for example because an update does not fit the
 * selected nodes. An overlay document that is invalid in itself is an {@link InvalidOverlayException}.
 */
public class OverlayException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String location;

    OverlayException(String location, String message) {
        super((location.isEmpty() ? "Overlay" : location) + ": " + message);
        this.location = location;
    }

    /**
     * Returns the JSON Pointer in the overlay that the error is about: the action, for example {@code /actions/2};
     * for an {@link InvalidOverlayException}, the first problem's field.
     */
    public String location() {
        return location;
    }
}
