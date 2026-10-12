package io.github.treetrail.openapi.overlay;

import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An overlay document does not follow the Overlay Specification. {@link #problems()} lists everything that is
 * wrong, not only the first.
 */
public final class InvalidOverlayException extends OverlayException {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // An immutable List.of copy, serializable.
    private final List<OverlayProblem> problems;

    InvalidOverlayException(List<OverlayProblem> problems) {
        super(problems.get(0).location(), message(problems));
        this.problems = List.copyOf(problems);
    }

    private static String message(List<OverlayProblem> problems) {
        String first = problems.get(0).message();
        if (problems.size() == 1) {
            return first;
        }
        return first + " (and " + (problems.size() - 1) + " more: "
                + problems.subList(1, problems.size()).stream()
                        .map(OverlayProblem::toString)
                        .collect(Collectors.joining("; "))
                + ")";
    }

    /** Returns the problems in the order {@link Overlay#validate} finds them; at least one. */
    public List<OverlayProblem> problems() {
        return problems;
    }
}
