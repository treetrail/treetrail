package io.github.treetrail.openapi.overlay.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Properties;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.tasks.TaskProvider;

/**
 * Plugin {@code io.github.treetrail.openapi-overlay}: applies OpenAPI Overlays to OpenAPI descriptions.
 *
 * <pre>{@code
 * openApiOverlay {
 *     register("petstore") {
 *         input = layout.projectDirectory.file("src/main/openapi/petstore.yaml")
 *         overlays.from("src/main/openapi/codegen.overlay.yaml")
 *         failOnNoMatch = true
 *     }
 * }
 * // Task applyPetstoreOverlay writes build/openapi-overlay/petstore/petstore.yaml.
 * }</pre>
 *
 * <p>The overlay library runs in its own class loader, with the class path of the configuration
 * {@code openApiOverlayRuntime}: by default {@code io.github.treetrail:openapi-overlay} in the plugin's version,
 * resolved from the project's repositories. So the plugin brings no Jackson onto the build's class path, where it
 * could meet the Jackson of other plugins.
 */
public class OpenApiOverlayPlugin implements Plugin<Project> {

    /** The name of the extension, a container of {@link OverlaySpec}s. */
    public static final String EXTENSION = "openApiOverlay";

    /** The configuration that holds the overlay library; add a dependency to it to use another version. */
    public static final String LIBRARY = "openApiOverlay";

    /** Creates the plugin. */
    public OpenApiOverlayPlugin() {}

    @Override
    public void apply(Project project) {
        String version = version();
        DependencyScopeConfiguration library = project.getConfigurations()
                .dependencyScope(LIBRARY, c -> {
                    c.setDescription("The OpenAPI Overlay library the overlay tasks run with.");
                    c.defaultDependencies(dependencies -> dependencies.add(
                            project.getDependencies().create("io.github.treetrail:openapi-overlay:" + version)));
                })
                .get();
        ResolvableConfiguration runtime = project.getConfigurations()
                .resolvable(LIBRARY + "Runtime", c -> c.extendsFrom(library))
                .get();

        NamedDomainObjectContainer<OverlaySpec> specs = project.getObjects().domainObjectContainer(OverlaySpec.class);
        project.getExtensions().add("openApiOverlay", specs);

        TaskProvider<?> all = project.getTasks().register("applyOverlays", task -> {
            task.setGroup("openapi");
            task.setDescription("Applies all OpenAPI overlays.");
        });
        specs.all(spec -> {
            spec.getFailOnNoMatch().convention(false);
            spec.getLenientMerge().convention(false);
            spec.getOutput()
                    .convention(project.getLayout()
                            .getBuildDirectory()
                            .file(spec.getInput()
                                    .map(input -> "openapi-overlay/" + spec.getName() + "/"
                                            + input.getAsFile().getName())));
            TaskProvider<ApplyOverlay> task = project.getTasks()
                    .register(taskName(spec.getName()), ApplyOverlay.class, apply -> {
                        apply.setGroup("openapi");
                        apply.setDescription("Applies the OpenAPI overlays of '" + spec.getName() + "'.");
                        apply.getInput().set(spec.getInput());
                        apply.getOverlays().from(spec.getOverlays());
                        apply.getOutput().set(spec.getOutput());
                        apply.getFailOnNoMatch().set(spec.getFailOnNoMatch());
                        apply.getLenientMerge().set(spec.getLenientMerge());
                        apply.getRuntimeClasspath().from((Configuration) runtime);
                    });
            all.configure(task1 -> task1.dependsOn(task));
        });
    }

    /** Returns the name of the task for an entry: {@code petstore} → {@code applyPetstoreOverlay}. */
    static String taskName(String name) {
        return "apply" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Overlay";
    }

    /** Returns the plugin's version, written into the jar by the build. */
    static String version() {
        try (InputStream in = OpenApiOverlayPlugin.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                throw new IllegalStateException("version.properties is missing from the plugin");
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
