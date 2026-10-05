package io.github.treetrail.jsonpath;

/**
 * Thrown when a JSONPath expression is not a valid RFC 9535 query.
 */
public final class JsonPathSyntaxException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String expression;
    private final int position;

    /**
     * Creates the exception.
     *
     * @param reason what is wrong
     * @param expression the full expression
     * @param position the character index in {@code expression} where the problem was found
     */
    public JsonPathSyntaxException(String reason, String expression, int position) {
        super(reason + " at position " + position + " in " + expression);
        this.expression = expression;
        this.position = position;
    }

    /** Returns the expression that failed to parse. */
    public String expression() {
        return expression;
    }

    /** Returns the character index where the problem was found. */
    public int position() {
        return position;
    }
}
