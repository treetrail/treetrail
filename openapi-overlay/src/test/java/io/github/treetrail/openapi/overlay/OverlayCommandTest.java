package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.jackson2.Jackson2Model;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverlayCommandTest {

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private Path openApi;
    private Path first;
    private Path second;

    @BeforeEach
    void files() throws Exception {
        openApi = Files.writeString(directory.resolve("openapi.yaml"), "info: {title: t}\nservers: [{url: a}]\n");
        first = Files.writeString(directory.resolve("first.yaml"), """
                overlay: 1.1.0
                info: {title: first, version: '1'}
                actions:
                  - {target: $.servers, remove: true}
                """);
        second = Files.writeString(directory.resolve("second.json"), """
                {"overlay": "1.1.0", "info": {"title": "second", "version": "1"},
                 "actions": [{"target": "$", "update": {"servers": [{"url": "http://localhost"}]}},
                             {"target": "$.missing", "remove": true}]}
                """);
    }

    private int run(String... args) {
        return OverlayCommand.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void appliesOverlaysInOrder() throws Exception {
        Path output = directory.resolve("out.json");
        int status = run(
                "--overlay",
                first.toString(),
                "--overlay",
                second.toString(),
                "--output",
                output.toString(),
                openApi.toString());
        assertThat(status).isZero();
        assertThat(OverlayDocuments.read(output))
                .isEqualTo(OverlayDocuments.parse("{info: {title: t}, servers: [{url: 'http://localhost'}]}"));
        assertThat(out.toString(StandardCharsets.UTF_8)).startsWith("Applied 2 overlay(s)");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("target $.missing selects no node");
    }

    @Test
    void preparesADescriptionForACodeGenerator() {
        Path codegen = OverlayFixturesTest.resource("codegen");
        Path output = directory.resolve("openapi.yaml");
        int status = run(
                "--strict",
                "--overlay",
                codegen.resolve("codegen.overlay.yaml").toString(),
                "--output",
                output.toString(),
                codegen.resolve("openapi.yaml").toString());
        assertThat(status).as(err.toString(StandardCharsets.UTF_8)).isZero();
        JsonNode result = OverlayDocuments.read(output);
        assertThat(JsonPath.compile("$.paths.*.*.operationId")
                        .query(result, Jackson2Model.INSTANCE)
                        .values())
                .extracting(JsonNode::textValue)
                .containsExactly("findAllPets", "createPet", "health");
        assertThat(JsonPath.compile("$.paths.*.get.responses['404']").query(result, Jackson2Model.INSTANCE))
                .hasSize(2);
        assertThat(JsonPath.compile("$..parameters[?!@.schema]").query(result, Jackson2Model.INSTANCE))
                .isEmpty();
        assertThat(JsonPath.compile("$..['x-oai-traits']").query(result, Jackson2Model.INSTANCE))
                .isEmpty();
        assertThat(result.at("/servers/0/url").textValue()).isEqualTo("http://localhost:8080");
    }

    @Test
    void createsTheOutputDirectory() {
        Path output = directory.resolve("build/generated/openapi.yaml");
        assertThat(run("--overlay", first.toString(), "--output", output.toString(), openApi.toString()))
                .isZero();
        assertThat(OverlayDocuments.read(output)).isEqualTo(OverlayDocuments.parse("{info: {title: t}}"));
    }

    @Test
    void outputDirectoryCannotBeCreated() throws Exception {
        Path file = Files.writeString(directory.resolve("file"), "");
        assertThat(run(
                        "--overlay",
                        first.toString(),
                        "--output",
                        file.resolve("out.yaml").toString(),
                        openApi.toString()))
                .isEqualTo(1);
    }

    @Test
    void strictFailsOnActionsThatChangeNothing() {
        Path output = directory.resolve("out.yaml");
        int status = run("--strict", "--overlay", second.toString(), "--output", output.toString(), openApi.toString());
        assertThat(status).isEqualTo(1);
        assertThat(output).doesNotExist();
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("second.json: /actions/1: target $.missing selects no node");
    }

    @Test
    void invalidOverlay() throws Exception {
        Path invalid = Files.writeString(directory.resolve("invalid.yaml"), "overlay: 1.1.0\n");
        int status = run("--overlay", invalid.toString(), "--output", "out.yaml", openApi.toString());
        assertThat(status).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("invalid.yaml: Overlay: 'info' is required (and 1 more: Overlay: 'actions' is required)");
    }

    @Test
    void missingFile() {
        int status = run("--overlay", "missing.yaml", "--output", "out.yaml", openApi.toString());
        assertThat(status).isEqualTo(1);
    }

    @Test
    void wrongArguments() {
        assertThat(run()).isEqualTo(2);
        assertThat(run("--overlay")).isEqualTo(2);
        assertThat(run("--unknown")).isEqualTo(2);
        assertThat(run("a.yaml", "b.yaml")).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Usage: OverlayCommand");
    }
}
