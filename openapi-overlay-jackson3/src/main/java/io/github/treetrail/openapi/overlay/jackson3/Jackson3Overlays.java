package io.github.treetrail.openapi.overlay.jackson3;

import io.github.treetrail.openapi.overlay.InvalidOverlayException;
import io.github.treetrail.openapi.overlay.Overlay;
import io.github.treetrail.openapi.overlay.OverlayOptions;
import io.github.treetrail.openapi.overlay.OverlayProblem;
import io.github.treetrail.openapi.overlay.OverlayResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * Applies overlays to Jackson 3 trees.
 *
 * <pre>{@code
 * Overlay overlay = Overlay.read(Path.of("codegen.overlay.yaml"));
 * JsonNode openApi = Jackson3OverlayDocuments.read(Path.of("openapi.yaml"));
 * Jackson3OverlayResult result = Jackson3Overlays.applyTo(overlay, openApi, OverlayOptions.DEFAULT.withFailOnNoMatch(true));
 * Jackson3OverlayDocuments.write(result.document(), Path.of("build/openapi.yaml"));
 * }</pre>
 *
 * <p>The overlay is applied by {@link Overlay#applyTo(com.fasterxml.jackson.databind.JsonNode, OverlayOptions)},
 * with the same rules, changes and warnings: the document is converted to a Jackson 2 tree and the result back to a
 * Jackson 3 tree. The conversion keeps every value exactly, including the type of each number ({@code int},
 * {@code long}, {@code BigInteger}, {@code float}, {@code double} and {@code BigDecimal} with its scale) and the
 * order of members. Binary, POJO and missing nodes are not JSON values and are rejected.
 */
public final class Jackson3Overlays {

    private Jackson3Overlays() {}

    /**
     * Returns the overlay of a parsed Jackson 3 document; see {@link Overlay#of(com.fasterxml.jackson.databind.JsonNode)}.
     *
     * @throws InvalidOverlayException if the overlay is invalid
     * @throws IllegalArgumentException if the document contains a node that is not a JSON value
     */
    public static Overlay overlayOf(JsonNode document) {
        return Overlay.of(toJackson2(document));
    }

    /**
     * Returns everything that makes a parsed Jackson 3 overlay document invalid; see
     * {@link Overlay#validate(com.fasterxml.jackson.databind.JsonNode)}.
     *
     * @throws IllegalArgumentException if the document contains a node that is not a JSON value
     */
    public static List<OverlayProblem> validate(JsonNode document) {
        return Overlay.validate(toJackson2(document));
    }

    /**
     * Applies the overlay to a copy of the document with the default options; the document itself is not changed.
     * See {@link Overlay#applyTo(com.fasterxml.jackson.databind.JsonNode, OverlayOptions)}.
     *
     * @throws io.github.treetrail.openapi.overlay.OverlayException if an action cannot be applied
     * @throws io.github.treetrail.jsonpath.JsonPathLimitExceededException if a target or copy query exceeds the
     *     evaluation limits
     * @throws IllegalArgumentException if the document contains a node that is not a JSON value
     */
    public static Jackson3OverlayResult applyTo(Overlay overlay, JsonNode document) {
        return applyTo(overlay, document, OverlayOptions.DEFAULT);
    }

    /**
     * Applies the overlay to a copy of the document; the document itself is not changed. See
     * {@link Overlay#applyTo(com.fasterxml.jackson.databind.JsonNode, OverlayOptions)}.
     *
     * @throws io.github.treetrail.openapi.overlay.OverlayException if an action cannot be applied, or, with
     *     {@link OverlayOptions#failOnNoMatch()}, a target selects no node
     * @throws io.github.treetrail.jsonpath.JsonPathLimitExceededException if a target or copy query exceeds the
     *     {@linkplain OverlayOptions#limits() limits}
     * @throws IllegalArgumentException if the document contains a node that is not a JSON value
     */
    public static Jackson3OverlayResult applyTo(Overlay overlay, JsonNode document, OverlayOptions options) {
        Objects.requireNonNull(overlay, "overlay");
        Objects.requireNonNull(options, "options");
        OverlayResult result = overlay.applyTo(toJackson2(document), options);
        return new Jackson3OverlayResult(toJackson3(result.document()), result.changes(), result.warnings());
    }

    /**
     * Returns the overlay's extensions ({@code x-} members of its root) as Jackson 3 trees; see
     * {@link Overlay#extensions()}.
     */
    public static Map<String, JsonNode> extensions(Overlay overlay) {
        Map<String, JsonNode> extensions = new LinkedHashMap<>();
        overlay.extensions().forEach((name, value) -> extensions.put(name, toJackson3(value)));
        return Collections.unmodifiableMap(extensions);
    }

    /**
     * Returns a Jackson 2 tree with the same values as a Jackson 3 tree, keeping the type of each number.
     *
     * @throws IllegalArgumentException if the tree contains a node that is not a JSON value
     */
    public static com.fasterxml.jackson.databind.JsonNode toJackson2(JsonNode tree) {
        return Trees.toJackson2(Objects.requireNonNull(tree, "tree"));
    }

    /**
     * Returns a Jackson 3 tree with the same values as a Jackson 2 tree, keeping the type of each number.
     *
     * @throws IllegalArgumentException if the tree contains a node that is not a JSON value
     */
    public static JsonNode toJackson3(com.fasterxml.jackson.databind.JsonNode tree) {
        return Trees.toJackson3(Objects.requireNonNull(tree, "tree"));
    }
}
