package io.github.treetrail.openapi.overlay.jackson3;

import io.github.treetrail.openapi.overlay.OverlayChange;
import io.github.treetrail.openapi.overlay.OverlayWarning;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * The result of applying an overlay to a Jackson 3 document, as
 * {@link io.github.treetrail.openapi.overlay.OverlayResult} for Jackson 2.
 *
 * @param document the changed document: a new tree; the document the overlay was applied to is unchanged
 * @param changes what each action did at each selected node, in the order of the actions
 * @param warnings the actions that changed nothing, for example because their target selects no node
 */
public record Jackson3OverlayResult(JsonNode document, List<OverlayChange> changes, List<OverlayWarning> warnings) {

    /** Creates a result. */
    public Jackson3OverlayResult {
        Objects.requireNonNull(document, "document");
        changes = List.copyOf(changes);
        warnings = List.copyOf(warnings);
    }
}
