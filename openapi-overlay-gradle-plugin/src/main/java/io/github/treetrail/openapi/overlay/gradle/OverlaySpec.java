package io.github.treetrail.openapi.overlay.gradle;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * One OpenAPI description and the overlays to apply to it, in {@code openApiOverlay { register("name") { ... } }}.
 * The plugin registers a task {@code apply<Name>Overlay} for it.
 */
public abstract class OverlaySpec implements Named {

    /** Created by the {@code openApiOverlay} container. */
    public OverlaySpec() {}

    /** Returns the name, which names the task and the default output directory; managed by Gradle. */
    @Override
    public abstract String getName();

    /** The OpenAPI description, JSON or YAML. */
    public abstract RegularFileProperty getInput();

    /** The overlays, applied in this order. */
    public abstract ConfigurableFileCollection getOverlays();

    /**
     * Where the result goes, in the format of its file name ({@code .json} or YAML); by default
     * {@code build/openapi-overlay/<name>/<input file name>}.
     */
    public abstract RegularFileProperty getOutput();

    /**
     * Whether an action whose target selects no node fails the build; default {@code false}, as the Overlay
     * Specification says. Set it to {@code true} to notice an overlay that no longer fits a changed description.
     */
    public abstract Property<Boolean> getFailOnNoMatch();

    /** Whether incompatible values replace instead of failing; default {@code false}. */
    public abstract Property<Boolean> getLenientMerge();
}
