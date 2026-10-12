package io.github.treetrail.openapi.overlay;

import io.github.treetrail.jsonpath.EvaluationLimits;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How {@link Overlay#applyTo(com.fasterxml.jackson.databind.JsonNode, OverlayOptions)} applies an overlay.
 *
 * <pre>{@code
 * // In a build: an overlay that no longer fits the description fails instead of changing nothing.
 * overlay.applyTo(openApi, OverlayOptions.DEFAULT.withFailOnNoMatch(true));
 * }</pre>
 *
 * <p>Instances are immutable.
 */
public final class OverlayOptions {

    /** The specification's behavior: a target that selects no node is a warning, a type mismatch an error. */
    public static final OverlayOptions DEFAULT = new OverlayOptions(false, false, EvaluationLimits.DEFAULT);

    private final boolean failOnNoMatch;
    private final boolean lenientMerge;
    private final EvaluationLimits limits;

    private OverlayOptions(boolean failOnNoMatch, boolean lenientMerge, EvaluationLimits limits) {
        this.failOnNoMatch = failOnNoMatch;
        this.lenientMerge = lenientMerge;
        this.limits = limits;
    }

    /**
     * Returns these options with an action whose target selects no node being an error ({@link OverlayException})
     * instead of a warning. The specification lets such actions succeed; in a build, they usually mean that the
     * overlay no longer fits a changed description.
     */
    public OverlayOptions withFailOnNoMatch(boolean failOnNoMatch) {
        return new OverlayOptions(failOnNoMatch, lenientMerge, limits);
    }

    /**
     * Returns these options with lenient merging: where the specification calls a combination of values
     * incompatible, for example an object update for a primitive target, the update replaces the target instead
     * of failing, and an update or copy may select nodes of different kinds. This is how Speakeasy's overlay tool
     * behaves outside its strict mode; use it for overlays written for that behavior.
     */
    public OverlayOptions withLenientMerge(boolean lenientMerge) {
        return new OverlayOptions(failOnNoMatch, lenientMerge, limits);
    }

    /**
     * Returns these options with other limits for evaluating the target and copy queries, for overlays from
     * untrusted sources.
     */
    public OverlayOptions withLimits(EvaluationLimits limits) {
        return new OverlayOptions(failOnNoMatch, lenientMerge, Objects.requireNonNull(limits, "limits"));
    }

    /** Returns whether an action whose target selects no node is an error. */
    public boolean failOnNoMatch() {
        return failOnNoMatch;
    }

    /** Returns whether incompatible values replace instead of failing. */
    public boolean lenientMerge() {
        return lenientMerge;
    }

    /** Returns the limits for evaluating target and copy queries. */
    public EvaluationLimits limits() {
        return limits;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof OverlayOptions other
                && failOnNoMatch == other.failOnNoMatch
                && lenientMerge == other.lenientMerge
                && limits.equals(other.limits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failOnNoMatch, lenientMerge, limits);
    }

    @Override
    public String toString() {
        return "OverlayOptions[failOnNoMatch=" + failOnNoMatch + ", lenientMerge=" + lenientMerge + ", limits=" + limits
                + "]";
    }
}
