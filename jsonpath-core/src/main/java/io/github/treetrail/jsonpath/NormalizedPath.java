package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The location of a node in a document as a normalized path (RFC 9535, section 2.7): the member names and
 * array indices that lead from the root to the node, for example {@code $['store']['book'][0]}.
 *
 * <pre>{@code
 * NormalizedPath path = NormalizedPath.parse("$['store']['book'][0]");
 * path.steps();          // [Name[name=store], Name[name=book], Index[index=0]]
 * path.toJsonPointer();  // "/store/book/0"
 * }</pre>
 *
 * <p>Every selected {@link Node} has one ({@link Node#normalizedPath()}). Instances are immutable; two paths
 * are equal if they have the same steps.
 */
public final class NormalizedPath {

    /** A step of a normalized path: a member {@link Name} or an array {@link Index}. */
    public sealed interface Step permits Name, Index {}

    /**
     * A member name.
     *
     * @param name the member name, any string
     */
    public record Name(String name) implements Step {

        /** Creates the step. */
        public Name {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * An array index.
     *
     * @param index the index, 0 or more
     */
    public record Index(int index) implements Step {

        /**
         * Creates the step.
         *
         * @throws IllegalArgumentException if {@code index} is negative
         */
        public Index {
            if (index < 0) {
                throw new IllegalArgumentException("Array index must not be negative: " + index);
            }
        }
    }

    private static final NormalizedPath ROOT = new NormalizedPath(List.of());

    private final List<Step> steps;
    // Computed on first use; a race only computes the same string twice.
    private @Nullable String string;

    private NormalizedPath(List<Step> steps) {
        this.steps = steps;
    }

    /** Returns the path of the root node, {@code $}. */
    public static NormalizedPath root() {
        return ROOT;
    }

    /** Returns the path made of these steps; no steps is the root. */
    public static NormalizedPath of(List<? extends Step> steps) {
        List<Step> copy = List.copyOf(steps);
        return copy.isEmpty() ? ROOT : new NormalizedPath(copy);
    }

    /**
     * Parses a normalized path, as {@link Node#path()} returns it. Only the normalized form is accepted: member
     * names in single quotes with the escapes of RFC 9535, section 2.7, and indices without sign or leading zeros.
     *
     * @throws IllegalArgumentException if {@code normalizedPath} is not a normalized path
     * @throws NullPointerException if {@code normalizedPath} is null
     */
    public static NormalizedPath parse(String normalizedPath) {
        return new Reader(Objects.requireNonNull(normalizedPath, "normalizedPath")).read();
    }

    /** Returns the steps from the root to the node. */
    public List<Step> steps() {
        return steps;
    }

    /** Returns the path to the member with the given name of the node at this path. */
    public NormalizedPath child(String name) {
        return append(new Name(name));
    }

    /**
     * Returns the path to the element with the given index of the node at this path.
     *
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public NormalizedPath child(int index) {
        return append(new Index(index));
    }

    private NormalizedPath append(Step step) {
        List<Step> longer = new ArrayList<>(steps.size() + 1);
        longer.addAll(steps);
        longer.add(step);
        return new NormalizedPath(List.copyOf(longer));
    }

    /**
     * Returns the path as a JSON Pointer (RFC 6901), for example {@code /store/book/0}; the root is the empty
     * string. {@code ~} and {@code /} in member names become {@code ~0} and {@code ~1}.
     */
    public String toJsonPointer() {
        StringBuilder sb = new StringBuilder();
        for (Step step : steps) {
            sb.append('/');
            if (step instanceof Index index) {
                sb.append(index.index());
            } else {
                String name = ((Name) step).name();
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (c == '~') {
                        sb.append("~0");
                    } else if (c == '/') {
                        sb.append("~1");
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Returns the normalized path, for example {@code $['store']['book'][0]}. */
    @Override
    public String toString() {
        String s = string;
        if (s == null) {
            StringBuilder sb = new StringBuilder("$");
            for (Step step : steps) {
                Location.appendStep(sb, step instanceof Index index ? (Object) index.index() : ((Name) step).name());
            }
            s = sb.toString();
            string = s;
        }
        return s;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof NormalizedPath other && steps.equals(other.steps);
    }

    @Override
    public int hashCode() {
        // The text is canonical, so it hashes consistently with equals; Node hashes the same text.
        return toString().hashCode();
    }

    /** Parser for the normalized-path grammar of RFC 9535, section 2.7. */
    private static final class Reader {

        private final String text;
        private int pos;

        Reader(String text) {
            this.text = text;
        }

        NormalizedPath read() {
            expect('$');
            List<Step> steps = new ArrayList<>();
            while (pos < text.length()) {
                expect('[');
                if (pos < text.length() && text.charAt(pos) == '\'') {
                    steps.add(new Name(name()));
                } else {
                    steps.add(new Index(index()));
                }
                expect(']');
            }
            return of(steps);
        }

        private String name() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("Unterminated member name");
                }
                char c = text.charAt(pos);
                if (c == '\'') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    sb.append(escape());
                } else if (c < 0x20) {
                    throw error("Control characters must be escaped");
                } else if (Character.isHighSurrogate(c)
                        && pos + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(pos + 1))) {
                    sb.append(c).append(text.charAt(pos + 1));
                    pos += 2;
                } else if (Character.isSurrogate(c)) {
                    throw error("Unpaired surrogate");
                } else {
                    sb.append(c);
                    pos++;
                }
            }
        }

        private char escape() {
            if (pos >= text.length()) {
                throw error("Unterminated escape");
            }
            char c = text.charAt(pos++);
            return switch (c) {
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '\'' -> '\'';
                case '\\' -> '\\';
                case 'u' -> unicodeEscape();
                default -> throw error("Invalid escape \\" + c);
            };
        }

        /** {@code \}{@code u00XX} for the control characters that have no short escape, lower-case hex. */
        private char unicodeEscape() {
            if (pos + 4 > text.length() || !text.startsWith("00", pos)) {
                throw error("Invalid \\u escape");
            }
            int high = text.charAt(pos + 2) - '0';
            int low = Character.digit(text.charAt(pos + 3), 16);
            boolean lowerCase = !Character.isUpperCase(text.charAt(pos + 3));
            if ((high != 0 && high != 1) || low < 0 || !lowerCase) {
                throw error("Invalid \\u escape");
            }
            char c = (char) (high * 16 + low);
            if (c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t') {
                throw error("Use the short escape for this character");
            }
            pos += 4;
            return c;
        }

        private int index() {
            int start = pos;
            while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                pos++;
            }
            if (pos == start) {
                throw error("Expected a member name in single quotes or an array index");
            }
            if (text.charAt(start) == '0' && pos - start > 1) {
                throw error("Array index with a leading zero");
            }
            try {
                return Integer.parseInt(text, start, pos, 10);
            } catch (NumberFormatException e) {
                throw error("Array index too large");
            }
        }

        private void expect(char c) {
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String reason) {
            return new IllegalArgumentException(
                    "Not a normalized path: " + reason + " at position " + pos + " in " + text);
        }
    }
}
