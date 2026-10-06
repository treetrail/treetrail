package io.github.treetrail.jsonpath;

/**
 * Thrown when running a query exceeds one of its {@link EvaluationLimits}.
 */
public final class JsonPathLimitExceededException extends JsonPathEvaluationException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message which limit was exceeded
     */
    public JsonPathLimitExceededException(String message) {
        super(message);
    }
}
