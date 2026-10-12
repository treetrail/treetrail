package io.github.treetrail.openapi.overlay.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

/**
 * Applies overlays to an OpenAPI description. Registered by the plugin for each entry of {@code openApiOverlay};
 * it can also be registered directly.
 */
@CacheableTask
public abstract class ApplyOverlay extends DefaultTask {

    /** Created by Gradle. */
    public ApplyOverlay() {}

    /** The OpenAPI description, JSON or YAML. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInput();

    /** The overlays, applied in this order. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getOverlays();

    /** The file names of {@link #getOverlays()} in order, so that reordering them reruns the task. */
    @Input
    public List<String> getOverlayOrder() {
        List<String> names = new ArrayList<>();
        for (File file : getOverlays()) {
            names.add(file.getName());
        }
        return names;
    }

    /** The result, in the format of its file name. */
    @OutputFile
    public abstract RegularFileProperty getOutput();

    /** Whether an action whose target selects no node fails the task. */
    @Input
    public abstract Property<Boolean> getFailOnNoMatch();

    /** Whether incompatible values replace instead of failing. */
    @Input
    public abstract Property<Boolean> getLenientMerge();

    /** The class path of the overlay library, isolated from the build's: the configuration openApiOverlayRuntime. */
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /** Used to run the overlay library in its own class loader. */
    // Gradle injects services through abstract getters; Error Prone only knows field and constructor injection.
    @SuppressWarnings("JavaxInjectOnAbstractMethod")
    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    void apply() {
        WorkQueue queue = getWorkerExecutor()
                .classLoaderIsolation(spec -> spec.getClasspath().from(getRuntimeClasspath()));
        queue.submit(ApplyOverlayWork.class, parameters -> {
            parameters.getInput().set(getInput());
            parameters.getOverlays().from(getOverlays());
            parameters.getOutput().set(getOutput());
            parameters.getFailOnNoMatch().set(getFailOnNoMatch());
            parameters.getLenientMerge().set(getLenientMerge());
        });
    }
}
