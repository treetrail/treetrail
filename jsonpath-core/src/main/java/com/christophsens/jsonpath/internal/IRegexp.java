package com.christophsens.jsonpath.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * I-Regexp (RFC 9485), the interoperable regular expression format used by the {@code match()}
 * and {@code search()} functions.
 *
 * <p>An expression is validated against the I-Regexp grammar and translated into an equivalent
 * {@link java.util.regex.Pattern}. Java syntax that I-Regexp does not allow is rejected, so an
 * expression behaves the same here as in any other conforming implementation.
 */
public final class IRegexp {

    private static final Set<String> CATEGORIES = Set.of(
            "L", "Ll", "Lm", "Lo", "Lt", "Lu",
            "M", "Mc", "Me", "Mn",
            "N", "Nd", "Nl", "No",
            "P", "Pc", "Pd", "Pe", "Pf", "Pi", "Po", "Ps",
            "Z", "Zl", "Zp", "Zs",
            "S", "Sc", "Sk", "Sm", "So",
            "C", "Cc", "Cf", "Cn", "Co");

    private static final int CACHE_SIZE = 256;

    private static final Map<String, Optional<Pattern>> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Optional<Pattern>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    private final String source;
    private int pos;
    private final StringBuilder out = new StringBuilder();

    private IRegexp(String source) {
        this.source = source;
    }

    /** Returns the compiled pattern, or empty if {@code regexp} is not a valid I-Regexp. */
    public static Optional<Pattern> compile(String regexp) {
        return CACHE.computeIfAbsent(regexp, IRegexp::translate);
    }

    private static Optional<Pattern> translate(String regexp) {
        try {
            IRegexp parser = new IRegexp(regexp);
            parser.regexp();
            if (parser.pos != regexp.length()) {
                return Optional.empty();
            }
            return Optional.of(Pattern.compile(parser.out.toString()));
        } catch (InvalidRegexp | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Translates an I-Regexp into Java regex syntax; for tests. */
    static String toJava(String regexp) {
        IRegexp parser = new IRegexp(regexp);
        parser.regexp();
        if (parser.pos != regexp.length()) {
            throw new InvalidRegexp();
        }
        return parser.out.toString();
    }

    private void regexp() {
        branch();
        while (peek() == '|') {
            pos++;
            out.append('|');
            branch();
        }
    }

    private void branch() {
        while (pos < source.length() && peek() != '|' && peek() != ')') {
            piece();
        }
    }

    private void piece() {
        atom();
        int c = peek();
        if (c == '*' || c == '+' || c == '?') {
            pos++;
            out.append((char) c);
        } else if (c == '{') {
            quantifier();
        }
    }

    private void quantifier() {
        pos++;
        int min = quantity();
        out.append('{').append(min);
        if (peek() == ',') {
            pos++;
            out.append(',');
            if (peek() != '}') {
                int max = quantity();
                if (max < min) {
                    throw new InvalidRegexp();
                }
                out.append(max);
            }
        }
        expect('}');
        out.append('}');
    }

    private int quantity() {
        int start = pos;
        while (pos < source.length() && source.charAt(pos) >= '0' && source.charAt(pos) <= '9') {
            pos++;
        }
        if (start == pos) {
            throw new InvalidRegexp();
        }
        try {
            return Integer.parseInt(source.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new InvalidRegexp();
        }
    }

    private void atom() {
        int c = next();
        switch (c) {
            case '(':
                out.append("(?:");
                regexp();
                expect(')');
                out.append(')');
                break;
            case '.':
                // I-Regexp '.' matches any character except line feed and carriage return.
                out.append("[^\\n\\r]");
                break;
            case '\\':
                escape(false);
                break;
            case '[':
                charClass();
                break;
            // The I-Regexp grammar lists '^' and '$' as normal characters, but the Compliance Test
            // Suite ("explicit caret", "explicit dollar") expects them to act as anchors, as in the
            // regex dialects RFC 9485 section 5 maps to. We follow the test suite.
            case '^':
                out.append("\\A");
                break;
            case '$':
                out.append("\\z");
                break;
            default:
                if (!isNormalChar(c)) {
                    throw new InvalidRegexp();
                }
                literal(c);
        }
    }

    private static boolean isNormalChar(int c) {
        return (c <= 0x27) || c == ',' || c == '-' || (c >= 0x2F && c <= 0x3E) || (c >= 0x40 && c <= 0x5A)
                || (c >= 0x5E && c <= 0x7A) || (c >= 0x7E && c <= 0xD7FF) || (c >= 0xE000 && c <= 0x10FFFF);
    }

    /**
     * Parses an escape after the backslash. Returns the escaped code point for a single character
     * escape, or -1 for a category escape (which is emitted directly).
     */
    private int escape(boolean inClass) {
        int c = next();
        int literal;
        switch (c) {
            case 'n':
                literal = '\n';
                break;
            case 'r':
                literal = '\r';
                break;
            case 't':
                literal = '\t';
                break;
            case '(': case ')': case '*': case '+': case '-': case '.': case '?':
            case '[': case '\\': case ']': case '^': case '{': case '|': case '}':
                literal = c;
                break;
            case 'p':
            case 'P':
                category(c == 'P');
                return -1;
            default:
                throw new InvalidRegexp();
        }
        if (!inClass) {
            literal(literal);
        }
        return literal;
    }

    private void category(boolean complement) {
        expect('{');
        int start = pos;
        while (pos < source.length() && source.charAt(pos) != '}') {
            pos++;
        }
        String name = source.substring(start, pos);
        expect('}');
        if (!CATEGORIES.contains(name)) {
            throw new InvalidRegexp();
        }
        out.append(complement ? "\\P{" : "\\p{").append(name).append('}');
    }

    private void charClass() {
        out.append('[');
        if (peek() == '^') {
            pos++;
            out.append('^');
        }
        boolean first = true;
        while (true) {
            int c = peek();
            if (c == ']' && !first) {
                pos++;
                break;
            }
            if (c == '-') {
                pos++;
                if (first || peek() == ']') {
                    // A literal '-' is only allowed first or last.
                    literal('-');
                    first = false;
                    continue;
                }
                throw new InvalidRegexp();
            }
            classItem();
            first = false;
        }
        out.append(']');
    }

    private void classItem() {
        int lo = classChar(true);
        if (lo < 0) {
            return;
        }
        if (peek() == '-' && pos + 1 < source.length() && source.charAt(pos + 1) != ']') {
            pos++;
            int hi = classChar(false);
            if (hi < lo) {
                throw new InvalidRegexp();
            }
            literal(lo);
            out.append('-');
            literal(hi);
        } else {
            literal(lo);
        }
    }

    /** Returns a class character, or -1 if a category escape was emitted (only when allowed). */
    private int classChar(boolean allowCategory) {
        int c = next();
        if (c == '\\') {
            int start = pos;
            int escaped = escape(true);
            if (escaped < 0 && !allowCategory) {
                pos = start;
                throw new InvalidRegexp();
            }
            return escaped;
        }
        if (c == '-' || c == '[' || c == ']' || (c >= 0xD800 && c <= 0xDFFF)) {
            throw new InvalidRegexp();
        }
        return c;
    }

    private void literal(int c) {
        out.append("\\x{").append(Integer.toHexString(c)).append('}');
    }

    private int peek() {
        return pos < source.length() ? source.codePointAt(pos) : -1;
    }

    private int next() {
        if (pos >= source.length()) {
            throw new InvalidRegexp();
        }
        int c = source.codePointAt(pos);
        if (Character.isSurrogate(source.charAt(pos)) && Character.charCount(c) == 1) {
            throw new InvalidRegexp();
        }
        pos += Character.charCount(c);
        return c;
    }

    private void expect(int c) {
        if (next() != c) {
            throw new InvalidRegexp();
        }
    }

    private static final class InvalidRegexp extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidRegexp() {
            super(null, null, false, false);
        }
    }
}
