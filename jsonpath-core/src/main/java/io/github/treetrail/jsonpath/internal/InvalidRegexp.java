package io.github.treetrail.jsonpath.internal;

/** Thrown while parsing or compiling an expression that is not a valid I-Regexp or exceeds a limit. */
final class InvalidRegexp extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidRegexp() {
        super(null, null, false, false);
    }
}
