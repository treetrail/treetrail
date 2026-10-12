package io.github.treetrail.openapi.overlay.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.openapi.overlay.OverlayDocuments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverlayRunnerTest {

    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();

    private Path write(String name, String text) throws Exception {
        return Files.writeString(directory.resolve(name), text);
    }

    @Test
    void appliesOverlaysInOrderAndWritesTheFormatOfTheOutputName() throws Exception {
        Path input = write("openapi.yaml", "info: {title: t}\nservers: [{url: a}]\n");
        Path first = write(
                "first.yaml",
                "overlay: 1.1.0\ninfo: {title: f, version: '1'}\nactions: [{target: $.servers, remove: true}]\n");
        Path second = write("second.yaml", """
                overlay: 1.1.0
                info: {title: s, version: '1'}
                actions:
                  - {target: $, update: {servers: [{url: b}]}}
                  - {target: $.nothing, remove: true}
                """);
        Path output = directory.resolve("out/dir/openapi.json");

        OverlayRunner.run(input, List.of(first, second), output, false, false, warnings::add);

        assertThat(Files.readString(output)).startsWith("{");
        assertThat(OverlayDocuments.read(output))
                .isEqualTo(OverlayDocuments.parse("{info: {title: t}, servers: [{url: b}]}"));
        assertThat(warnings).containsExactly("Overlay " + second + ": /actions/1: target $.nothing selects no node");
    }

    @Test
    void failures() throws Exception {
        Path input = write("openapi.yaml", "a: 1\n");
        Path noMatch = write(
                "nomatch.yaml",
                "overlay: 1.1.0\ninfo: {title: t, version: '1'}\nactions: [{target: $.b, remove: true}]\n");
        Path invalid = write("invalid.yaml", "overlay: 1.1.0\n");
        Path out = directory.resolve("out.yaml");

        assertThatThrownBy(() -> OverlayRunner.run(input, List.of(noMatch), out, true, false, warnings::add))
                .isInstanceOf(OverlayRunner.Failure.class)
                .hasMessage("Overlay " + noMatch + ": /actions/0: target $.b selects no node");
        assertThatThrownBy(() -> OverlayRunner.run(input, List.of(invalid), out, false, false, warnings::add))
                .hasMessageStartingWith("Overlay " + invalid + ": Overlay: 'info' is required");
        assertThatThrownBy(() -> OverlayRunner.run(
                        input, List.of(directory.resolve("missing.yaml")), out, false, false, warnings::add))
                .hasMessageStartingWith("Overlay " + directory.resolve("missing.yaml") + ": ");
        assertThatThrownBy(() -> OverlayRunner.run(
                        directory.resolve("missing.yaml"), List.of(), out, false, false, warnings::add))
                .hasMessageStartingWith("Cannot read the OpenAPI description ");
        Path file = write("file", "");
        assertThatThrownBy(() ->
                        OverlayRunner.run(input, List.of(), file.resolve("out.yaml"), false, false, warnings::add))
                .hasMessageStartingWith("Cannot write ");
        assertThat(out).doesNotExist();
    }

    @Test
    void lenientMerge() throws Exception {
        Path input = write("openapi.yaml", "a: true\n");
        Path overlay = write(
                "o.yaml", "overlay: 1.1.0\ninfo: {title: t, version: '1'}\nactions: [{target: $.a, update: {b: 1}}]\n");
        Path out = directory.resolve("out.yaml");

        assertThatThrownBy(() -> OverlayRunner.run(input, List.of(overlay), out, false, false, warnings::add))
                .hasMessageContaining("cannot merge an object");
        OverlayRunner.run(input, List.of(overlay), out, false, true, warnings::add);
        assertThat(OverlayDocuments.read(out)).isEqualTo(OverlayDocuments.parse("{a: {b: 1}}"));
    }
}
