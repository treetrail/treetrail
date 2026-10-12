package io.github.treetrail.openapi.overlay.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/** Runs in the isolated class loader with the overlay library and calls {@link OverlayRunner}. */
public abstract class ApplyOverlayWork implements WorkAction<ApplyOverlayWork.Parameters> {

    private static final Logger LOGGER = Logging.getLogger(ApplyOverlayWork.class);

    /** The parameters, copied from {@link ApplyOverlay}. */
    public interface Parameters extends WorkParameters {

        /** The OpenAPI description. */
        RegularFileProperty getInput();

        /** The overlays in order. */
        ConfigurableFileCollection getOverlays();

        /** The result. */
        RegularFileProperty getOutput();

        /** Whether a target that selects nothing fails. */
        Property<Boolean> getFailOnNoMatch();

        /** Whether incompatible values replace. */
        Property<Boolean> getLenientMerge();
    }

    /** Created by Gradle. */
    public ApplyOverlayWork() {}

    @Override
    public void execute() {
        Parameters parameters = getParameters();
        List<File> overlays = new ArrayList<>(parameters.getOverlays().getFiles());
        try {
            OverlayRunner.run(
                    parameters.getInput().get().getAsFile().toPath(),
                    overlays.stream().map(File::toPath).toList(),
                    parameters.getOutput().get().getAsFile().toPath(),
                    parameters.getFailOnNoMatch().get(),
                    parameters.getLenientMerge().get(),
                    LOGGER::warn);
        } catch (OverlayRunner.Failure e) {
            throw new GradleException(String.valueOf(e.getMessage()), e);
        }
    }
}
