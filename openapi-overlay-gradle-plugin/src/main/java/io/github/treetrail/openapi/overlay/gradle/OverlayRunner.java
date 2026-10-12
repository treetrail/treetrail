package io.github.treetrail.openapi.overlay.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.treetrail.jsonpath.JsonPathLimitExceededException;
import io.github.treetrail.openapi.overlay.Overlay;
import io.github.treetrail.openapi.overlay.OverlayDocuments;
import io.github.treetrail.openapi.overlay.OverlayException;
import io.github.treetrail.openapi.overlay.OverlayOptions;
import io.github.treetrail.openapi.overlay.OverlayResult;
import io.github.treetrail.openapi.overlay.OverlayWarning;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Applies overlays to a file and writes the result; the work of {@link ApplyOverlay}, without Gradle. */
final class OverlayRunner {

    /** An overlay or file could not be read, or an overlay could not be applied. */
    static final class Failure extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        Failure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private OverlayRunner() {}

    static void run(
            Path input,
            List<Path> overlays,
            Path output,
            boolean failOnNoMatch,
            boolean lenientMerge,
            Consumer<String> warnings)
            throws Failure {
        OverlayOptions options =
                OverlayOptions.DEFAULT.withFailOnNoMatch(failOnNoMatch).withLenientMerge(lenientMerge);
        JsonNode document;
        try {
            document = OverlayDocuments.read(input);
        } catch (UncheckedIOException e) {
            throw new Failure("Cannot read the OpenAPI description " + input + ": " + e.getMessage(), e);
        }
        for (Path file : overlays) {
            OverlayResult result;
            try {
                result = Overlay.read(file).applyTo(document, options);
            } catch (OverlayException | JsonPathLimitExceededException | UncheckedIOException e) {
                throw new Failure("Overlay " + file + ": " + e.getMessage(), e);
            }
            for (OverlayWarning warning : result.warnings()) {
                warnings.accept("Overlay " + file + ": " + warning);
            }
            document = result.document();
        }
        try {
            Path directory = output.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            OverlayDocuments.write(document, output);
        } catch (IOException | UncheckedIOException e) {
            throw new Failure("Cannot write " + output + ": " + e.getMessage(), e);
        }
    }
}
