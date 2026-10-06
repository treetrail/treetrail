package io.github.treetrail.jsonpath;

/**
 * Thrown when a valid query cannot be run to completion: the document contains a value the
 * {@link JsonModel} rejects, an {@link EvaluationLimits evaluation limit} was exceeded, or the thread
 * was interrupted.
 *
 * <p>Errors from the model are wrapped, with the normalized path of the node being processed in
 * {@link #path()} and the original exception as the cause. If the thread was interrupted, its interrupt
 * status stays set.
 */
public class JsonPathEvaluationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String path;

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     */
    public JsonPathEvaluationException(String message) {
        super(message);
        this.path = null;
    }

    /**
     * Creates the exception for a failure at a node of the document.
     *
     * @param message what went wrong
     * @param path the normalized path of the node being processed
     * @param cause the exception from the model
     */
    public JsonPathEvaluationException(String message, String path, Throwable cause) {
        super(message + " at " + path, cause);
        this.path = path;
    }

    /**
     * Returns the normalized path of the node being processed when the failure occurred, for example
     * {@code $['store']['book'][0]}, or {@code null} if the failure is not tied to a node.
     */
    public String path() {
        return path;
    }
}
