package io.github.treetrail.openapi.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import com.code_intelligence.jazzer.mutation.annotation.WithUtf8Length;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.treetrail.jsonpath.EvaluationLimits;
import io.github.treetrail.jsonpath.JsonPathLimitExceededException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Fuzz target for overlays and documents from untrusted sources: applying may only throw the documented
 * exceptions, must never change the input document, and must finish within {@link #TIME_LIMIT_NANOS}.
 *
 * <p>{@code ./gradlew test} replays the seeds (the fixtures' overlays and descriptions) and saved findings;
 * {@code ./gradlew :openapi-overlay:fuzz} fuzzes, {@code -PfuzzDuration=10m} sets the time.
 */
class OverlayFuzzTest {

    private static final long TIME_LIMIT_NANOS = 5_000_000_000L;
    private static final OverlayOptions OPTIONS =
            OverlayOptions.DEFAULT.withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(100_000));

    @org.junit.jupiter.params.provider.MethodSource("fixtures")
    @FuzzTest
    void applyAnyOverlay(
            @NotNull @WithUtf8Length(max = 5_000) String overlayText,
            @NotNull @WithUtf8Length(max = 5_000) String documentText,
            boolean lenient) {
        long start = System.nanoTime();
        try {
            Overlay overlay = Overlay.parse(overlayText);
            JsonNode document = OverlayDocuments.parse(documentText);
            JsonNode before = document.deepCopy();
            try {
                OverlayResult result = overlay.applyTo(document, OPTIONS.withLenientMerge(lenient));
                // The result can be written and read back.
                OverlayDocuments.write(result.document(), OverlayDocuments.Format.JSON);
            } catch (OverlayException | JsonPathLimitExceededException expected) {
                // an action that does not fit the document, or a query beyond the limits
            }
            assertEquals(before, document, "the input document must not change");
        } catch (InvalidOverlayException | UncheckedIOException expected) {
            // an invalid overlay, or a document that is neither JSON nor YAML
        }
        long elapsed = System.nanoTime() - start;
        if (elapsed > TIME_LIMIT_NANOS) {
            throw new AssertionError("Took " + elapsed / 1_000_000 + " ms: " + overlayText + " on " + documentText);
        }
    }

    static Stream<Arguments> fixtures() throws IOException {
        Path root = OverlayFixturesTest.resource("spec-examples").getParent();
        List<Path> sets;
        try (Stream<Path> paths = Files.walk(root)) {
            sets = paths.filter(p -> p.getFileName().toString().equals("overlay.yaml"))
                    .map(Path::getParent)
                    .sorted()
                    .toList();
        }
        return sets.stream().map(set -> {
            try {
                return Arguments.of(
                        Files.readString(set.resolve("overlay.yaml")),
                        Files.readString(set.resolve("openapi.yaml")),
                        false);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
