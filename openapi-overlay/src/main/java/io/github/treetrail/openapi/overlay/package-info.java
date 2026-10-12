/**
 * OpenAPI Overlay (Overlay Specification 1.0, 1.1 and 1.2) for Jackson 2 trees, with RFC 9535 JSONPath targets.
 *
 * <pre>{@code
 * Overlay overlay = Overlay.read(Path.of("codegen.overlay.yaml"));
 * JsonNode openApi = OverlayDocuments.read(Path.of("openapi.yaml"));
 * OverlayResult result = overlay.applyTo(openApi, OverlayOptions.DEFAULT.withFailOnNoMatch(true));
 * OverlayDocuments.write(result.document(), Path.of("build/openapi.yaml"));
 * }</pre>
 *
 * <p>{@link io.github.treetrail.openapi.overlay.Overlay} reads and validates an overlay,
 * {@link io.github.treetrail.openapi.overlay.OverlayDocuments} reads and writes JSON and YAML documents, and
 * {@link io.github.treetrail.openapi.overlay.OverlayCommand} applies overlays from the command line, for example
 * in a build before a code generator.
 */
@NullMarked
package io.github.treetrail.openapi.overlay;

import org.jspecify.annotations.NullMarked;
