package io.github.treetrail.jsonpath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Random JSON documents and random RFC 9535 queries over them, for differential testing against another
 * implementation. Queries use the member names of {@link JsonArbitraries#NAMES}, so they often select
 * something; a share of them is damaged on purpose, so that rejecting invalid queries is compared too.
 *
 * <p>Numbers stay within the range where binary floating point and exact decimals agree, because the
 * other implementation may parse numbers as doubles.
 *
 * <p>String literals escape control characters with their short escapes or with Unicode escapes, and
 * other characters now and then with Unicode escapes (surrogate pairs above U+FFFF), in hex digits of
 * either case. Together with shorthand names above U+FFFF ({@code $.😀}), trailing commas
 * ({@code $[1, ]}) among the damage, and boolean literals in ordering comparisons, this keeps the bugs
 * the test found in the reference (fixed in jsonpath-rfc9535 2.0.1) covered.
 */
final class RandomQueries {

    private static final String[] SHORTHAND_NAMES = {"a", "b", "c", "_x", "é", "😀"};
    private static final String[] FUNCTIONS_TESTS = {"match", "search"};
    private static final String[] PATTERNS = {
        "a.*", "[a-c]+", "\\\\p{L}", ".", "é|😀", "^a", "b$", "[^a]*", "(ab|c){1,2}"
    };
    private static final String[] TRAILING_COMMAS = {",", ", ", " ,", " , "};
    private static final Pattern SHORTHAND = Pattern.compile("\\.(?:" + String.join("|", SHORTHAND_NAMES) + ")");
    private static final String[] OPERATORS = {"==", "!=", "<", "<=", ">", ">="};

    private final Random random;

    RandomQueries(long seed) {
        this.random = new Random(seed);
    }

    // ---- documents ----

    Object document() {
        return random.nextBoolean() ? array(3, 1) : object(3, 1);
    }

    private Object value(int depth) {
        if (depth == 0 || random.nextInt(5) < 2) {
            return scalar();
        }
        return random.nextBoolean() ? array(depth, 0) : object(depth, 0);
    }

    private Object array(int depth, int minSize) {
        List<Object> list = new ArrayList<>();
        int size = minSize + random.nextInt(5 - minSize);
        for (int i = 0; i < size; i++) {
            list.add(value(depth - 1));
        }
        return list;
    }

    private Object object(int depth, int minSize) {
        Map<String, Object> map = new LinkedHashMap<>();
        int size = minSize + random.nextInt(5 - minSize);
        for (int i = 0; i < size; i++) {
            map.put(memberName(), value(depth - 1));
        }
        return map;
    }

    /** Mostly common names, so that queries select something; sometimes an awkward one. */
    private String memberName() {
        return random.nextInt(10) < 7 ? pick(SHORTHAND_NAMES) : pick(JsonArbitraries.NAMES);
    }

    private Object scalar() {
        switch (random.nextInt(9)) {
            case 0:
                return null;
            case 1:
                return random.nextBoolean();
            case 2:
            case 3:
                return random.nextInt(7) - 3;
            case 4:
                return new BigDecimal(random.nextInt(601) - 300).movePointLeft(1 + random.nextInt(2));
            case 5:
                return random.nextBoolean() ? 1.0 : 1e2;
            default:
                return random.nextInt(3) == 0 ? pick(JsonArbitraries.NAMES) : string();
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        int length = random.nextInt(4);
        for (int i = 0; i < length; i++) {
            sb.append("abcé😀"
                    .codePoints()
                    .skip(random.nextInt(5))
                    .limit(1)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append));
        }
        return sb.toString();
    }

    // ---- queries ----

    String query() {
        String query = random.nextBoolean() ? "$" + segments(3) : "$" + segments(1) + segment(2);
        return random.nextInt(7) == 0 ? damage(query) : query;
    }

    private String segments(int depth) {
        StringBuilder sb = new StringBuilder();
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            sb.append(segment(depth));
        }
        return sb.toString();
    }

    private String segment(int depth) {
        String blank = random.nextInt(6) == 0 ? " " : "";
        switch (random.nextInt(8)) {
            case 0:
                return "." + pick(SHORTHAND_NAMES);
            case 1:
                return ".*";
            case 2:
                return ".." + (random.nextBoolean() ? pick(SHORTHAND_NAMES) : "*");
            case 3:
                return "..[" + selectors(depth) + "]";
            default:
                return blank + "[" + blank + selectors(depth) + blank + "]";
        }
    }

    private String selectors(int depth) {
        StringBuilder sb = new StringBuilder(selector(depth));
        while (random.nextInt(4) == 0) {
            sb.append(random.nextBoolean() ? "," : ", ").append(selector(depth));
        }
        return sb.toString();
    }

    private String selector(int depth) {
        switch (random.nextInt(depth > 0 ? 6 : 5)) {
            case 0:
                return name();
            case 1:
                return "*";
            case 2:
                return String.valueOf(random.nextInt(7) - 3);
            case 3:
                return slice();
            case 4:
                return name();
            default:
                return "?" + logical(depth - 1);
        }
    }

    private String name() {
        String name = memberName();
        StringBuilder sb = new StringBuilder(random.nextBoolean() ? "'" : "\"");
        char quote = sb.charAt(0);
        for (int i = 0; i < name.length(); i += Character.charCount(name.codePointAt(i))) {
            int c = name.codePointAt(i);
            if (c == quote || c == '\\') {
                sb.append('\\').append((char) c);
            } else if (c < 0x20 || random.nextInt(6) == 0) {
                // Control characters must be escaped, any other character may be.
                String shortEscape = shortEscape(c);
                if (shortEscape != null && random.nextBoolean()) {
                    sb.append(shortEscape);
                } else {
                    for (char unit : Character.toChars(c)) {
                        sb.append(unicodeEscape(unit));
                    }
                }
            } else {
                sb.appendCodePoint(c);
            }
        }
        return sb.append(quote).toString();
    }

    private static String shortEscape(int c) {
        switch (c) {
            case '\b':
                return "\\b";
            case '\t':
                return "\\t";
            case '\n':
                return "\\n";
            case '\f':
                return "\\f";
            case '\r':
                return "\\r";
            default:
                return null;
        }
    }

    /** A Unicode escape of one UTF-16 code unit, each hex digit in upper or lower case. */
    private String unicodeEscape(char unit) {
        StringBuilder sb = new StringBuilder("\\u");
        for (char digit : String.format(Locale.ROOT, "%04x", (int) unit).toCharArray()) {
            sb.append(random.nextBoolean() ? digit : Character.toUpperCase(digit));
        }
        return sb.toString();
    }

    private String slice() {
        StringBuilder sb = new StringBuilder();
        if (random.nextBoolean()) {
            sb.append(random.nextInt(9) - 4);
        }
        sb.append(':');
        if (random.nextBoolean()) {
            sb.append(random.nextInt(9) - 4);
        }
        if (random.nextBoolean()) {
            sb.append(':');
            if (random.nextBoolean()) {
                sb.append(random.nextInt(7) - 3);
            }
        }
        return sb.toString();
    }

    private String logical(int depth) {
        String left = basic(depth);
        switch (random.nextInt(5)) {
            case 0:
                return left + " && " + basic(depth);
            case 1:
                return left + " || " + basic(depth);
            default:
                return left;
        }
    }

    private String basic(int depth) {
        switch (random.nextInt(9)) {
            case 0:
                return relative(depth);
            case 1:
                return "!" + relative(depth);
            case 2:
                return "(" + logical(depth) + ")";
            case 3:
                return pick(FUNCTIONS_TESTS) + "(" + singular() + ", '" + pick(PATTERNS) + "')";
            case 4:
                // Booleans are not ordered: only == and != can be true for them (RFC 9535 2.3.5.2.2).
                String bool = random.nextBoolean() ? "true" : "false";
                String other = comparable();
                String operator = pick(OPERATORS);
                return random.nextBoolean() ? bool + " " + operator + " " + other : other + " " + operator + " " + bool;
            default:
                return comparable() + " " + pick(OPERATORS) + " " + comparable();
        }
    }

    private String comparable() {
        switch (random.nextInt(6)) {
            case 0:
                return literal();
            case 1:
                return "length(" + singular() + ")";
            case 2:
                return "count(" + relative(0) + ")";
            case 3:
                return "value(" + relative(0) + ")";
            default:
                return random.nextInt(4) == 0 ? literal() : singular();
        }
    }

    private String singular() {
        StringBuilder sb = new StringBuilder(random.nextInt(8) == 0 ? "$" : "@");
        int count = random.nextInt(3);
        for (int i = 0; i < count; i++) {
            sb.append(random.nextBoolean() ? "." + pick(SHORTHAND_NAMES) : "[" + (random.nextInt(5) - 2) + "]");
        }
        return sb.toString();
    }

    private String relative(int depth) {
        StringBuilder sb = new StringBuilder("@");
        int count = random.nextInt(3);
        for (int i = 0; i < count; i++) {
            sb.append(segment(Math.max(depth, 0)));
        }
        return sb.toString();
    }

    private String literal() {
        switch (random.nextInt(6)) {
            case 0:
                return "null";
            case 1:
                return random.nextBoolean() ? "true" : "false";
            case 2:
                return String.valueOf(random.nextInt(7) - 3);
            case 3:
                return random.nextBoolean() ? "1.0" : "-0.5";
            default:
                return name();
        }
    }

    /**
     * Deletes, duplicates or inserts one character, adds a trailing comma to a bracketed selection or a
     * {@code -} to a member-name shorthand, which often makes the query invalid.
     */
    private String damage(String query) {
        String damaged = damageOnce(query);
        // Never split a surrogate pair: the case files are UTF-8, which cannot hold a lone surrogate.
        return hasUnpairedSurrogate(damaged) ? query : damaged;
    }

    private static boolean hasUnpairedSurrogate(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                i++;
            } else if (Character.isSurrogate(c)) {
                return true;
            }
        }
        return false;
    }

    private String damageOnce(String query) {
        int at = random.nextInt(query.length());
        switch (random.nextInt(5)) {
            case 0:
                return query.substring(0, at) + query.substring(at + 1);
            case 1:
                return query.substring(0, at + 1) + query.charAt(at) + query.substring(at + 1);
            case 2:
                String insertions = "[]().,?@$'\"!=<> -0";
                return query.substring(0, at)
                        + insertions.charAt(random.nextInt(insertions.length()))
                        + query.substring(at);
            case 3:
                int close = query.indexOf(']', at);
                if (close < 0) {
                    close = query.lastIndexOf(']');
                }
                return close < 0 ? query : query.substring(0, close) + pick(TRAILING_COMMAS) + query.substring(close);
            default:
                Matcher shorthand = SHORTHAND.matcher(query);
                if (!shorthand.find(at) && !shorthand.find(0)) {
                    return query;
                }
                return query.substring(0, shorthand.end()) + "-" + query.substring(shorthand.end());
        }
    }

    private <T> T pick(T[] values) {
        return values[random.nextInt(values.length)];
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
