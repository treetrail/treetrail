package io.github.treetrail.openapi.overlay.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/** What the plugin registers, checked without running a build. */
class OpenApiOverlayPluginTest {

    @Test
    void registersATaskPerEntryWithConventions() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("io.github.treetrail.openapi-overlay");
        @SuppressWarnings("unchecked")
        NamedDomainObjectContainer<OverlaySpec> specs = (NamedDomainObjectContainer<OverlaySpec>)
                project.getExtensions().getByName("openApiOverlay");

        specs.register("petstore", spec -> {
            spec.getInput().set(project.file("src/main/openapi/petstore.yaml"));
            spec.getOverlays().from("a.overlay.yaml", "b.overlay.json");
        });
        ApplyOverlay task = (ApplyOverlay) project.getTasks().getByName("applyPetstoreOverlay");

        assertThat(task.getGroup()).isEqualTo("openapi");
        assertThat(task.getInput().get().getAsFile()).isEqualTo(project.file("src/main/openapi/petstore.yaml"));
        assertThat(task.getOutput().get().getAsFile())
                .isEqualTo(project.file("build/openapi-overlay/petstore/petstore.yaml"));
        assertThat(task.getOverlayOrder()).containsExactly("a.overlay.yaml", "b.overlay.json");
        assertThat(task.getFailOnNoMatch().get()).isFalse();
        assertThat(task.getLenientMerge().get()).isFalse();
        assertThat(project.getTasks()
                        .getByName("applyOverlays")
                        .getTaskDependencies()
                        .getDependencies(null))
                .extracting(Task::getName)
                .containsExactly("applyPetstoreOverlay");
    }

    @Test
    void theLibraryVersionIsThePluginsVersion() {
        // Which dependency the configuration gets is checked by the functional tests, which resolve it.
        assertThat(OpenApiOverlayPlugin.version()).isEqualTo(System.getProperty("treetrail.version"));
    }

    @Test
    void taskNames() {
        assertThat(OpenApiOverlayPlugin.taskName("petstore")).isEqualTo("applyPetstoreOverlay");
        assertThat(OpenApiOverlayPlugin.taskName("p")).isEqualTo("applyPOverlay");
    }
}
