package io.github.treetrail.jsonpath;

/**
 * Thrown when a valid query cannot be run to completion, for example because the thread was
 * interrupted or an {@link EvaluationLimits evaluation limit} was exceeded.
 *
 * <p>If the thread was interrupted, its interrupt status stays set.
 */
public class JsonPathEvaluationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     */
    public JsonPathEvaluationException(String message) {
        super(message);
    }
}
