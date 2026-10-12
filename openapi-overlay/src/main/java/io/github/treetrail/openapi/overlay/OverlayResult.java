package io.github.treetrail.openapi.overlay;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

/**
 * The result of applying an overlay.
 *
 * @param document the changed document: a new tree; the document the overlay was applied to is unchanged
 * @param changes what each action did at each selected node, in the order of the actions
 * @param warnings the actions that changed nothing, for example because their target selects no node
 */
public record OverlayResult(JsonNode document, List<OverlayChange> changes, List<OverlayWarning> warnings) {

    /** Creates a result. */
    public OverlayResult {
        Objects.requireNonNull(document, "document");
        changes = List.copyOf(changes);
        warnings = List.copyOf(warnings);
    }
}
