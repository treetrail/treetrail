package io.github.treetrail.openapi.overlay.jackson3;

import io.github.treetrail.openapi.overlay.OverlayDocuments;
import io.github.treetrail.openapi.overlay.OverlayDocuments.Format;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;

/**
 * Reads and writes OpenAPI descriptions and overlays as JSON or YAML, as Jackson 3 trees, with exactly the settings
 * of {@link OverlayDocuments}: duplicate member names are rejected, decimals keep their digits, only {@code true} and
 * {@code false} are YAML booleans, and YAML documents may be up to 256 MiB. The text is read and written by
 * {@link OverlayDocuments}, so the output is the same byte for byte.
 */
public final class Jackson3OverlayDocuments {

    private Jackson3OverlayDocuments() {}

    /**
     * Parses JSON or YAML text; see {@link OverlayDocuments#parse(String)}.
     *
     * @throws UncheckedIOException if the text is neither
     */
    public static JsonNode parse(String text) {
        return Trees.toJackson3(OverlayDocuments.parse(text));
    }

    /**
     * Parses text in the given format.
     *
     * @throws UncheckedIOException if the text is not valid in that format
     */
    public static JsonNode parse(String text, Format format) {
        return Trees.toJackson3(OverlayDocuments.parse(text, format));
    }

    /** Reads a JSON or YAML file; see {@link OverlayDocuments#parse(String)}. */
    public static JsonNode read(Path file) {
        return Trees.toJackson3(OverlayDocuments.read(file));
    }

    /**
     * Returns the document as text in the given format, indented by two spaces.
     *
     * @throws IllegalArgumentException if the document contains a node that is not a JSON value
     */
    public static String write(JsonNode document, Format format) {
        return OverlayDocuments.write(Trees.toJackson2(document), format);
    }

    /**
     * Writes the document to a file, in the {@linkplain OverlayDocuments#formatOf(Path) format of its name}.
     *
     * @throws IllegalArgumentException if the document contains a node that is not a JSON value
     */
    public static void write(JsonNode document, Path file) {
        OverlayDocuments.write(Trees.toJackson2(document), file);
    }
}
