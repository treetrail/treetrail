package io.github.treetrail.openapi.overlay.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.treetrail.openapi.overlay.Overlay;
import io.github.treetrail.openapi.overlay.OverlayDocuments;
import io.github.treetrail.openapi.overlay.OverlayResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/** The fixtures of openapi-overlay (the build passes their directory), applied to Jackson 3 trees. */
class Jackson3OverlayFixturesTest {

    @TestFactory
    Stream<DynamicTest> compliantSets() throws IOException {
        return applications(fixtures().resolve("oai/compliant-sets"));
    }

    @TestFactory
    Stream<DynamicTest> specificationExamples() throws IOException {
        return applications(fixtures().resolve("spec-examples"));
    }

    /** Each directory: overlay.yaml applied to openapi.yaml gives output.yaml, with the changes of Jackson 2. */
    private static Stream<DynamicTest> applications(Path directory) throws IOException {
        List<Path> sets;
        try (Stream<Path> children = Files.list(directory)) {
            sets = children.filter(Files::isDirectory).sorted().toList();
        }
        assertThat(sets).isNotEmpty();
        return sets.stream()
                .map(set -> DynamicTest.dynamicTest(set.getFileName().toString(), () -> {
                    Overlay overlay =
                            Jackson3Overlays.overlayOf(Jackson3OverlayDocuments.read(set.resolve("overlay.yaml")));
                    JsonNode input = Jackson3OverlayDocuments.read(set.resolve("openapi.yaml"));
                    JsonNode before = input.deepCopy();
                    JsonNode expected = Jackson3OverlayDocuments.read(set.resolve("output.yaml"));

                    Jackson3OverlayResult result = Jackson3Overlays.applyTo(overlay, input);

                    assertThat(result.document()).isEqualTo(expected);
                    assertThat(input).as("the input is unchanged").isEqualTo(before);
                    OverlayResult jackson2 = overlay.applyTo(OverlayDocuments.read(set.resolve("openapi.yaml")));
                    assertThat(result.changes()).isEqualTo(jackson2.changes());
                    assertThat(result.warnings()).isEqualTo(jackson2.warnings()).isEmpty();
                }));
    }

    private static Path fixtures() {
        return Path.of(
                Objects.requireNonNull(System.getProperty("treetrail.overlayFixtures"), "treetrail.overlayFixtures"));
    }
}
