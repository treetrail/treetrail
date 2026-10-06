package io.github.treetrail.jsonpath;

/**
 * Thrown by {@link JavaObjectModel#parse(String)} when a text is not valid JSON (RFC 8259) or breaks one
 * of the I-JSON rules (RFC 7493) the parser enforces.
 */
public final class InvalidJsonException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String reason;
    private final int position;

    /**
     * Creates the exception.
     *
     * @param reason what is wrong
     * @param position the character index in the text where the problem was found
     */
    public InvalidJsonException(String reason, int position) {
        super(reason + " at position " + position);
        this.reason = reason;
        this.position = position;
    }

    /** Returns what is wrong, without the position. */
    public String reason() {
        return reason;
    }

    /** Returns the character index in the text where the problem was found. */
    public int position() {
        return position;
    }
}
