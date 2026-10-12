package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Invalid overlays: every problem is reported, not only the first. */
class OverlayValidationTest {

    @Test
    void reportsEveryProblem() {
        String overlay = """
                overlay: 1.1.0
                info: {title: 1}
                extra: true
                actions:
                  - {target: info, remove: maybe}
                  - {target: $.a, copy: '$[', description: 3}
                  - {target: $.b, update: 1, unknown: 1}
                  - 7
                """;

        // The first line of each problem; syntax errors go on with the query and a caret.
        assertThat(Overlay.validate(OverlayDocuments.parse(overlay)))
                .extracting(problem -> problem.toString().lines().findFirst().orElseThrow())
                .containsExactly(
                        "Overlay: unknown field 'extra'",
                        "/info/title: must be a string",
                        "/info: 'version' is required",
                        "/actions/0/target: not an RFC 9535 JSONPath query: Query must start with '$' at position 0:",
                        "/actions/0/remove: must be a boolean",
                        "/actions/1/description: must be a string",
                        "/actions/1/copy: not an RFC 9535 JSONPath query: Expected a selector at position 2:",
                        "/actions/2: unknown field 'unknown'",
                        "/actions/3: must be an object");
        assertThatThrownBy(() -> Overlay.parse(overlay))
                .isInstanceOf(InvalidOverlayException.class)
                .hasMessageStartingWith("Overlay: unknown field 'extra' (and 8 more: /info/title: must be a string; ")
                .satisfies(e -> {
                    InvalidOverlayException invalid = (InvalidOverlayException) e;
                    assertThat(invalid.problems()).hasSize(9);
                    assertThat(invalid.location()).isEmpty();
                });
    }

    @Test
    void reportsProblemsOfReusableActionsAndReferencesTogether() {
        String overlay = """
                overlay: 1.2.0
                $self: 'a#b'
                info: {title: t, version: '1'}
                components:
                  actions:
                    good: {fields: {update: {x: 1}}}
                    bad: {fields: {target: $, copy: 1}}
                    worse: 5
                actions:
                  - {$ref: '#/components/actions/good', target: $.a}
                  - {$ref: '#/components/actions/missing', target: $.b}
                  - {$ref: '#/components/actions/good', target: 'b'}
                  - {$ref: 1, target: $.c}
                """;

        assertThat(Overlay.validate(OverlayDocuments.parse(overlay)))
                .extracting(OverlayProblem::location)
                .containsExactly(
                        "/$self",
                        "/components/actions/bad/fields",
                        "/components/actions/bad/fields/copy",
                        "/components/actions/worse",
                        "/actions/1/$ref",
                        "/actions/2/target",
                        "/actions/3/$ref");
    }

    @Test
    void anInvalidVersionStillReportsTheOtherProblems() {
        assertThat(Overlay.validate(OverlayDocuments.parse("{overlay: 2.0.0, info: {title: t}, actions: []}")))
                .extracting(OverlayProblem::toString)
                .containsExactly(
                        "/overlay: must be a version 1.x.y, not '2.0.0'",
                        "/info: 'version' is required",
                        "/actions: must contain at least one action");
    }

    @Test
    void hugeMinorVersions() {
        // Found by fuzzing: NumberFormatException instead of a problem.
        String version = "1." + "4".repeat(110) + ".0";
        assertThat(Overlay.validate(OverlayDocuments.parse(
                        "{overlay: '" + version + "', info: {title: t, version: '1'}, actions: [{target: $}]}")))
                .extracting(OverlayProblem::toString)
                .containsExactly("/overlay: Overlay " + version + " is not supported; the latest supported is 1.2");
        assertThat(Overlay.validate(OverlayDocuments.parse(
                        "{overlay: '1.01.0', info: {title: t, version: '1'}, actions: [{target: $}]}")))
                .extracting(OverlayProblem::toString)
                .containsExactly("/overlay: must be a version 1.x.y, not '1.01.0'");
    }

    @Test
    void aValidOverlayHasNoProblems() {
        assertThat(Overlay.validate(OverlayDocuments.parse(
                        "{overlay: 1.0.0, info: {title: t, version: '1'}, actions: [{target: $}]}")))
                .isEmpty();
    }

    @Test
    void textThatIsNeitherJsonNorYaml() {
        assertThatThrownBy(() -> Overlay.parse("a: [b"))
                .isInstanceOf(InvalidOverlayException.class)
                .hasMessageStartingWith("Overlay: not a JSON or YAML document: ");
        assertThatThrownBy(() -> Overlay.parse(""))
                .isInstanceOf(InvalidOverlayException.class)
                .hasMessage("Overlay: not a JSON or YAML document: java.io.IOException: Empty document");
    }

    @Test
    void files(@TempDir Path directory) throws Exception {
        Path broken = Files.writeString(directory.resolve("broken.yaml"), "a: [b");
        assertThatThrownBy(() -> Overlay.read(broken))
                .isInstanceOf(InvalidOverlayException.class)
                .hasMessageStartingWith("Overlay: not a JSON or YAML document: ");
        assertThatThrownBy(() -> Overlay.read(directory.resolve("missing.yaml")))
                .isInstanceOf(UncheckedIOException.class);
        Path valid = Files.writeString(
                directory.resolve("valid.json"),
                "{\"overlay\": \"1.1.0\", \"info\": {\"title\": \"t\", \"version\": \"1\"}, \"actions\": [{\"target\": \"$\"}]}");
        Overlay overlay = Overlay.read(valid);
        assertThat(overlay).hasToString("Overlay 1.1.0 't' 1 (1 actions)");
    }

    @Test
    void nullDocuments() {
        assertThatThrownBy(() -> Overlay.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Overlay.validate(null)).isInstanceOf(NullPointerException.class);
    }
}
