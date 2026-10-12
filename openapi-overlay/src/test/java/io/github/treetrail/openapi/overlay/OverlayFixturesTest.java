package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * The examples of the specification and the fixtures of OAI/Overlay-Specification (see the READMEs in
 * src/test/resources).
 */
class OverlayFixturesTest {

    /**
     * Fixtures that pass the JSON Schema, whose only check on a target is that it starts with {@code $}, but whose
     * target is not an RFC 9535 query, so an implementation that "MUST fully implement RFC 9535" rejects them.
     * {@code @.x-oai-traits}: a name in shorthand notation cannot contain {@code -} (RFC 9535, section 2.5.1.1);
     * it would need {@code @['x-oai-traits']}.
     */
    private static final Map<String, String> NOT_RFC_9535 = Map.of(
            "v1.0/pass/actions-traits-example.yaml", "$.paths.*.get[?@.x-oai-traits.paged]",
            "v1.1/pass/actions-traits-example.yaml", "$.paths.*.get[?@.x-oai-traits.paged]",
            "v1.2-dev/pass/actions-traits-example.yaml", "$.paths.*.get[?@.x-oai-traits.paged]");

    @TestFactory
    Stream<DynamicTest> compliantSets() throws IOException {
        return applications(resource("oai/compliant-sets"));
    }

    @TestFactory
    Stream<DynamicTest> specificationExamples() throws IOException {
        return applications(resource("spec-examples"));
    }

    /** Each directory: overlay.yaml applied to openapi.yaml gives output.yaml. */
    private static Stream<DynamicTest> applications(Path directory) throws IOException {
        List<Path> sets;
        try (Stream<Path> children = Files.list(directory)) {
            sets = children.filter(Files::isDirectory).sorted().toList();
        }
        assertThat(sets).isNotEmpty();
        return sets.stream()
                .map(set -> DynamicTest.dynamicTest(set.getFileName().toString(), () -> {
                    Overlay overlay = Overlay.read(set.resolve("overlay.yaml"));
                    JsonNode input = OverlayDocuments.read(set.resolve("openapi.yaml"));
                    JsonNode before = input.deepCopy();
                    JsonNode expected = OverlayDocuments.read(set.resolve("output.yaml"));

                    OverlayResult result = overlay.applyTo(input);

                    assertThat(result.document()).isEqualTo(expected);
                    assertThat(result.warnings()).isEmpty();
                    assertThat(input).as("the input is unchanged").isEqualTo(before);
                }));
    }

    @TestFactory
    Stream<DynamicTest> schemaTests() throws IOException {
        Path root = resource("oai/schema-tests");
        List<Path> files;
        try (Stream<Path> all = Files.walk(root)) {
            files = all.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
        }
        assertThat(files).hasSizeGreaterThan(100);
        return files.stream().map(file -> {
            String name = root.relativize(file).toString().replace('\\', '/');
            return DynamicTest.dynamicTest(name, () -> {
                String text = Files.readString(file);
                if (NOT_RFC_9535.containsKey(name)) {
                    assertThatThrownBy(() -> Overlay.parse(text))
                            .isInstanceOf(OverlayException.class)
                            .hasMessageContaining("not an RFC 9535 JSONPath query")
                            .extracting(e -> ((OverlayException) e).location())
                            .isEqualTo("/actions/0/target");
                    // The same overlay with the RFC 9535 form of the target.
                    String fixed = text.replace(NOT_RFC_9535.get(name), "$.paths.*.get[?@['x-oai-traits'].paged]");
                    assertThat(Overlay.parse(fixed).actions()).hasSize(1);
                } else if (name.contains("/pass/")) {
                    assertThat(Overlay.parse(text).actions()).isNotEmpty();
                } else {
                    assertThatThrownBy(() -> Overlay.parse(text)).isInstanceOf(OverlayException.class);
                }
            });
        });
    }

    static Path resource(String name) {
        URL url = Objects.requireNonNull(OverlayFixturesTest.class.getResource("/" + name), name);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
