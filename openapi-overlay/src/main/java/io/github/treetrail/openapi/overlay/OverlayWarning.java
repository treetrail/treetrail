package io.github.treetrail.openapi.overlay;

import java.util.Objects;

/**
 * An action that changed nothing, for example because its target selects no node.
 *
 * @param actionIndex the position of the action in the overlay's {@code actions}, from 0
 * @param target the target query of the action
 * @param message what happened
 */
public record OverlayWarning(int actionIndex, String target, String message) {

    /** Creates a warning. */
    public OverlayWarning {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(message, "message");
    }

    /** Returns the warning as one line, for example {@code /actions/2: target $.paths['/a'] selects no node}. */
    @Override
    public String toString() {
        return "/actions/" + actionIndex + ": " + message;
    }
}
