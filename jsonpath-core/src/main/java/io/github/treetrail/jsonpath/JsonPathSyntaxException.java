package io.github.treetrail.jsonpath;

/**
 * Thrown when a JSONPath expression is not a valid RFC 9535 query.
 *
 * <p>The message shows the reason and an excerpt of at most 60 characters around
 * the problem with a caret under it, so that long expressions do not end up in logs in full:
 *
 * <pre>
 * Expected a query, a literal or a function at position 18:
 *   $.store[?@.price &lt;]
 *                     ^
 * </pre>
 */
public final class JsonPathSyntaxException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** Maximum number of expression characters shown in the message. */
    private static final int EXCERPT_LENGTH = 60;

    private final String reason;
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
        super(reason + " at position " + position + ":\n" + excerpt(expression, position));
        this.reason = reason;
        this.expression = expression;
        this.position = position;
    }

    /** Returns what is wrong, without position and excerpt. */
    public String reason() {
        return reason;
    }

    /** Returns the expression that failed to parse. */
    public String expression() {
        return expression;
    }

    /** Returns the character index where the problem was found. */
    public int position() {
        return position;
    }

    /** Two lines: the expression around {@code position}, shortened with "...", and a caret under it. */
    private static String excerpt(String expression, int position) {
        int start = 0;
        int end = expression.length();
        if (end > EXCERPT_LENGTH) {
            start = Math.max(0, Math.min(position - EXCERPT_LENGTH / 2, end - EXCERPT_LENGTH));
            end = start + EXCERPT_LENGTH;
        }
        StringBuilder line = new StringBuilder("  ");
        if (start > 0) {
            line.append("...");
        }
        int caret = line.length() + (position - start);
        for (int i = start; i < end; i++) {
            char c = expression.charAt(i);
            // Blanks such as newlines and tabs would break the alignment of the caret.
            line.append(c < 0x20 ? ' ' : c);
        }
        if (end < expression.length()) {
            line.append("...");
        }
        return line + "\n" + " ".repeat(caret) + "^";
    }
}
