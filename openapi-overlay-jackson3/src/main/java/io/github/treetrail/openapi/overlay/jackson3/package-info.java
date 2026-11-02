/**
 * OpenAPI Overlay for Jackson 3 trees: {@link io.github.treetrail.openapi.overlay.jackson3.Jackson3Overlays} applies
 * overlays to Jackson 3 documents, and {@link io.github.treetrail.openapi.overlay.jackson3.Jackson3OverlayDocuments}
 * reads and writes them with the settings of {@link io.github.treetrail.openapi.overlay.OverlayDocuments}.
 *
 * <pre>{@code
 * Overlay overlay = Overlay.read(Path.of("codegen.overlay.yaml"));
 * JsonNode openApi = Jackson3OverlayDocuments.read(Path.of("openapi.yaml"));
 * Jackson3OverlayResult result = Jackson3Overlays.applyTo(overlay, openApi);
 * Jackson3OverlayDocuments.write(result.document(), Path.of("build/openapi.yaml"));
 * }</pre>
 */
@NullMarked
package io.github.treetrail.openapi.overlay.jackson3;

import org.jspecify.annotations.NullMarked;
