package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverlayDocumentsTest {

    @Test
    void jsonAndYamlGiveTheSameTree() {
        JsonNode json = OverlayDocuments.parse("{\"a\": [1, \"x\", true, null], \"b\": {\"c\": 1.50}}");
        JsonNode yaml = OverlayDocuments.parse("a: [1, x, true, null]\nb:\n  c: 1.50\n");
        assertThat(json).isEqualTo(yaml);
    }

    @Test
    void yamlFlowMappingIsNotJson() {
        assertThat(OverlayDocuments.parse("{a: 1}").get("a").intValue()).isEqualTo(1);
    }

    @Test
    void byteOrderMarkAndLeadingWhitespace() {
        assertThat(OverlayDocuments.parse("﻿  {\"a\": 1}").get("a").intValue()).isEqualTo(1);
    }

    @Test
    void decimalsKeepTheirDigits() {
        JsonNode node = OverlayDocuments.parse("{\"a\": 1.10, \"b\": 0.1, \"c\": 1e400}");
        assertThat(OverlayDocuments.write(node, OverlayDocuments.Format.JSON))
                .isEqualTo("{\n  \"a\": 1.10,\n  \"b\": 0.1,\n  \"c\": " + "1" + "0".repeat(400) + "\n}\n");
    }

    @Test
    void yamlScalarsAsInOpenApiDescriptions() {
        JsonNode node = OverlayDocuments.parse("""
                version: 1.0.0
                responses:
                  200: {description: OK}
                yes: no
                countries: [NO, SE, Off, true, False]
                date: 2026-01-14
                """);
        assertThat(node.get("version").isTextual()).isTrue();
        assertThat(node.get("responses").has("200")).isTrue();
        // Only true and false are booleans (YAML 1.2), not the YAML 1.1 words yes, no, on and off.
        assertThat(node.get("yes").textValue()).isEqualTo("no");
        assertThat(node.get("countries")).isEqualTo(OverlayDocuments.parse("[\"NO\", \"SE\", \"Off\", true, false]"));
        assertThat(node.get("date").textValue()).isEqualTo("2026-01-14");
        // Written back, the words are quoted, so that YAML 1.1 readers keep them strings too.
        String yaml = OverlayDocuments.write(node, OverlayDocuments.Format.YAML);
        assertThat(yaml).contains("\"NO\"").contains("\"Off\"");
        assertThat(OverlayDocuments.parse(yaml)).isEqualTo(node);
    }

    @Test
    void duplicateMembersAreRejected() {
        assertThatThrownBy(() -> OverlayDocuments.parse("{\"a\": 1, \"a\": 2}"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Duplicate field 'a'");
        assertThatThrownBy(() -> OverlayDocuments.parse("a: 1\na: 2\n"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Duplicate field 'a'");
    }

    @Test
    void emptyAndInvalidDocuments() {
        assertThatThrownBy(() -> OverlayDocuments.parse("")).hasMessageContaining("Empty document");
        assertThatThrownBy(() -> OverlayDocuments.parse("a: [")).isInstanceOf(UncheckedIOException.class);
        assertThatThrownBy(() -> OverlayDocuments.parse("{\"a\": ", OverlayDocuments.Format.JSON))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void yamlOutputQuotesStringsThatWouldReadAsOtherTypes() {
        JsonNode node = OverlayDocuments.parse("{\"a\": \"1.0\", \"b\": \"true\", \"c\": \"text\", \"d\": [1]}");
        String yaml = OverlayDocuments.write(node, OverlayDocuments.Format.YAML);
        assertThat(OverlayDocuments.parse(yaml)).isEqualTo(node);
        assertThat(yaml).contains("c: text").contains("- 1");
    }

    @Test
    void filesInTheFormatOfTheirName(@TempDir Path directory) throws Exception {
        JsonNode node = OverlayDocuments.parse("{a: [1, 2]}");
        Path json = directory.resolve("out.JSON");
        Path yaml = directory.resolve("out.yaml");
        OverlayDocuments.write(node, json);
        OverlayDocuments.write(node, yaml);
        assertThat(Files.readString(json)).startsWith("{");
        assertThat(Files.readString(yaml)).startsWith("a:");
        assertThat(OverlayDocuments.read(json)).isEqualTo(node);
        assertThat(OverlayDocuments.read(yaml)).isEqualTo(node);
        assertThat(OverlayDocuments.formatOf(Path.of("/"))).isEqualTo(OverlayDocuments.Format.YAML);
        assertThatThrownBy(() -> OverlayDocuments.read(directory.resolve("missing.yaml")))
                .isInstanceOf(UncheckedIOException.class);
        assertThatThrownBy(() -> OverlayDocuments.write(node, directory.resolve("missing/out.yaml")))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void largeYamlDocuments() {
        // Beyond SnakeYAML's default limit of 3 MiB, which large descriptions such as Stripe's exceed.
        String yaml = "a: '" + "x".repeat(4 * 1024 * 1024) + "'\n";
        assertThat(OverlayDocuments.parse(yaml).get("a").textValue()).hasSize(4 * 1024 * 1024);
    }
}
