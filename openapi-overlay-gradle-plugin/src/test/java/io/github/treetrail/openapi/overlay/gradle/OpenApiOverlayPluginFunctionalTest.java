package io.github.treetrail.openapi.overlay.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds that use the plugin, run with Gradle TestKit. The overlay library comes from Maven Local, where this
 * build publishes the current version first.
 */
class OpenApiOverlayPluginFunctionalTest {

    private static final String VERSION = Objects.requireNonNull(System.getProperty("treetrail.version"));
    private static final Path CODEGEN =
            Path.of(Objects.requireNonNull(System.getProperty("treetrail.codegenFixtures")));
    private static final Path TEST_KIT = Path.of(Objects.requireNonNull(System.getProperty("treetrail.testKitDir")));

    @TempDir
    Path project;

    @BeforeEach
    void settings() throws IOException {
        write("settings.gradle.kts", """
                rootProject.name = "sample"
                dependencyResolutionManagement {
                    repositories {
                        mavenLocal { content { includeGroup("io.github.treetrail") } }
                        mavenCentral()
                    }
                }
                """);
        Files.createDirectories(project.resolve("src/main/openapi"));
        Files.copy(CODEGEN.resolve("openapi.yaml"), project.resolve("src/main/openapi/pets.yaml"));
        Files.copy(CODEGEN.resolve("codegen.overlay.yaml"), project.resolve("src/main/openapi/codegen.overlay.yaml"));
    }

    private void write(String name, String text) throws IOException {
        Path file = project.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private GradleRunner gradle(String... arguments) {
        List<String> args = new ArrayList<>(List.of(arguments));
        args.add("--stacktrace");
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withTestKitDir(TEST_KIT.toFile())
                .withPluginClasspath()
                .withArguments(args);
    }

    private static final String PETS = """
            plugins {
                id("io.github.treetrail.openapi-overlay")
            }
            openApiOverlay {
                register("pets") {
                    input = layout.projectDirectory.file("src/main/openapi/pets.yaml")
                    overlays.from("src/main/openapi/codegen.overlay.yaml")
                    failOnNoMatch = true
                }
            }
            """;

    @Test
    void appliesTheOverlayAndIsUpToDateAfterwards() throws IOException {
        write("build.gradle.kts", PETS);

        BuildResult first = gradle("applyPetsOverlay").build();
        assertThat(first.task(":applyPetsOverlay").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        String output = Files.readString(project.resolve("build/openapi-overlay/pets/pets.yaml"));
        assertThat(output)
                .contains("operationId: findAllPets")
                .doesNotContain("showPet")
                .contains("http://localhost:8080");

        BuildResult second = gradle("applyPetsOverlay").build();
        assertThat(second.task(":applyPetsOverlay").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        // Another overlay after the first reruns the task.
        write("src/main/openapi/later.overlay.yaml", """
                overlay: 1.1.0
                info: {title: later, version: '1'}
                actions: [{target: $.info, update: {x-later: true}}]
                """);
        write(
                "build.gradle.kts",
                PETS.replace(
                        "overlays.from(\"src/main/openapi/codegen.overlay.yaml\")",
                        "overlays.from(\"src/main/openapi/codegen.overlay.yaml\", \"src/main/openapi/later.overlay.yaml\")"));
        BuildResult third = gradle("applyOverlays").build();
        assertThat(third.task(":applyPetsOverlay").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(project.resolve("build/openapi-overlay/pets/pets.yaml")))
                .contains("x-later: true");
    }

    @Test
    void worksWithTheConfigurationCacheAndTheBuildCache() throws IOException {
        write("build.gradle.kts", PETS.replace("plugins {", "plugins {\n    base"));
        write("gradle.properties", "org.gradle.configuration-cache=true\norg.gradle.caching=true\n");
        write("settings.gradle.kts", Files.readString(project.resolve("settings.gradle.kts")) + """
                buildCache { local { directory = file("build-cache") } }
                """);

        BuildResult first = gradle("applyPetsOverlay").build();
        assertThat(first.getOutput()).contains("Configuration cache entry stored");
        gradle("clean").build();
        BuildResult second = gradle("applyPetsOverlay").build();
        assertThat(second.getOutput()).contains("Configuration cache entry reused");
        assertThat(second.task(":applyPetsOverlay").getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
    }

    @Test
    void failsOnATargetThatSelectsNothing() throws IOException {
        write("build.gradle.kts", PETS);
        write("src/main/openapi/codegen.overlay.yaml", """
                overlay: 1.1.0
                info: {title: stale, version: '1'}
                actions: [{target: "$.paths['/gone']", remove: true}]
                """);

        BuildResult result = gradle("applyPetsOverlay").buildAndFail();
        assertThat(result.getOutput())
                .contains("codegen.overlay.yaml: /actions/0: target $.paths['/gone'] selects no node");
    }

    @Test
    void failsOnAnInvalidOverlayWithAllItsProblems() throws IOException {
        write("build.gradle.kts", PETS);
        write("src/main/openapi/codegen.overlay.yaml", "overlay: 1.1.0\nactions: [{target: info}]\n");

        BuildResult result = gradle("applyPetsOverlay").buildAndFail();
        assertThat(result.getOutput())
                .contains("Overlay: 'info' is required (and 1 more: /actions/0/target: not an RFC 9535 JSONPath query");
    }

    /** The plugin's use case: an overlay before openapi-generator, whose plugin brings its own Jackson. */
    @Test
    void preparesADescriptionForOpenApiGenerator() throws IOException {
        write("build.gradle.kts", """
                plugins {
                    id("org.openapi.generator") version "7.26.0"
                    id("io.github.treetrail.openapi-overlay")
                }
                openApiOverlay {
                    register("pets") {
                        input = layout.projectDirectory.file("src/main/openapi/pets.yaml")
                        overlays.from("src/main/openapi/codegen.overlay.yaml")
                        failOnNoMatch = true
                    }
                }
                val applyPetsOverlay = tasks.named<io.github.treetrail.openapi.overlay.gradle.ApplyOverlay>("applyPetsOverlay")
                openApiGenerate {
                    generatorName = "java"
                    library = "native"
                    // The output carries the task dependency: openApiGenerate runs applyPetsOverlay first.
                    inputSpec = applyPetsOverlay.flatMap { it.output }
                    outputDir = layout.buildDirectory.dir("generated/pets")
                    apiPackage = "demo.api"
                    modelPackage = "demo.model"
                    invokerPackage = "demo"
                }
                """);

        BuildResult result = gradle("openApiGenerate").build();

        assertThat(result.task(":applyPetsOverlay").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":openApiGenerate").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        Path api = project.resolve("build/generated/pets/src/main/java/demo/api");
        String pets = Files.readString(api.resolve("PetsApi.java"));
        assertThat(pets)
                .contains("public void findAllPets(")
                .contains("public void createPet(")
                .doesNotContain("showPet");
        assertThat(Files.readString(project.resolve("build/generated/pets/src/main/java/demo/ApiClient.java")))
                .contains("http://localhost:8080");
    }

    @Test
    void worksOnGradle8() throws IOException {
        write("build.gradle.kts", PETS);
        // Gradle 8 does not run on Java 25.
        write(
                "gradle.properties",
                "org.gradle.java.home=" + System.getProperty("treetrail.java21").replace("\\", "/") + "\n");

        BuildResult result =
                gradle("applyPetsOverlay").withGradleVersion("8.14.3").build();

        assertThat(result.task(":applyPetsOverlay").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(project.resolve("build/openapi-overlay/pets/pets.yaml")).exists();
    }

    private static final String LIBRARIES = """
            tasks.register("libraries") {
                val files = configurations.named("openApiOverlayRuntime").map { c -> c.files.map { it.name }.sorted() }
                doLast { println("LIBRARIES " + files.get()) }
            }
            """;

    @Test
    void theLibraryIsTheOneOfThePluginsVersion() throws IOException {
        write("build.gradle.kts", PETS + LIBRARIES);

        BuildResult result = gradle("libraries").build();
        assertThat(result.getOutput())
                .contains("openapi-overlay-" + VERSION + ".jar")
                .contains("jsonpath-core-" + VERSION + ".jar")
                .contains("jackson-dataformat-yaml-");
    }

    @Test
    void anotherLibraryVersionCanBeDeclared() throws IOException {
        // Any version works the same way; the current one is the only one that has the module yet.
        write("build.gradle.kts", PETS + LIBRARIES + """
                dependencies {
                    openApiOverlay("io.github.treetrail:openapi-overlay:%s")
                }
                """.formatted(VERSION));

        BuildResult result = gradle("libraries", "applyPetsOverlay").build();
        assertThat(result.getOutput()).contains("openapi-overlay-" + VERSION + ".jar");
        assertThat(result.task(":applyPetsOverlay").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }
}
