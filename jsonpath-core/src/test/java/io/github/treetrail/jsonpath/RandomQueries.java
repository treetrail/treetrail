package io.github.treetrail.jsonpath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Random JSON documents and random RFC 9535 queries over them, for differential testing against another
 * implementation. Queries use the member names of {@link JsonArbitraries#NAMES}, so they often select
 * something; a share of them is damaged on purpose, so that rejecting invalid queries is compared too.
 *
 * <p>Numbers stay within the range where binary floating point and exact decimals agree, because the
 * other implementation may parse numbers as doubles.
 *
 * <p>Known bugs of the reference (jsonpath-rfc9535 2.0.0) are avoided rather than tolerated: it rejects
 * {@code \u0000} to {@code \u001f} escapes and, since 2.0.0, some lower-case hex digits ({@code \u00e9}).
 * So control characters only appear in queries through their short escapes ({@code \n}, {@code \t}),
 * and names with other control characters only in documents.
 */
final class RandomQueries {

    private static final String[] SHORTHAND_NAMES = {"a", "b", "c", "_x", "é"};
    private static final String[] FUNCTIONS_TESTS = {"match", "search"};
    private static final String[] PATTERNS = {"a.*", "[a-c]+", "\\\\p{L}", ".", "é|😀", "^a", "b$", "[^a]*", "(ab|c){1,2}"};
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
            sb.append("abcé😀".codePoints().skip(random.nextInt(5)).limit(1)
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
        String name;
        do {
            name = memberName();
        } while (hasControlCharacterWithoutShortEscape(name));
        StringBuilder sb = new StringBuilder(random.nextBoolean() ? "'" : "\"");
        char quote = sb.charAt(0);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == quote || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        return sb.append(quote).toString();
    }

    private static boolean hasControlCharacterWithoutShortEscape(String name) {
        return name.chars().anyMatch(c -> c < 0x20 && c != '\n' && c != '\t');
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
        switch (random.nextInt(8)) {
            case 0:
                return relative(depth);
            case 1:
                return "!" + relative(depth);
            case 2:
                return "(" + logical(depth) + ")";
            case 3:
                return pick(FUNCTIONS_TESTS) + "(" + singular() + ", '" + pick(PATTERNS) + "')";
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

    /** Deletes, duplicates or inserts one character, which often makes the query invalid. */
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
        switch (random.nextInt(3)) {
            case 0:
                return query.substring(0, at) + query.substring(at + 1);
            case 1:
                return query.substring(0, at + 1) + query.charAt(at) + query.substring(at + 1);
            default:
                String insertions = "[]().,?@$'\"!=<> -0";
                return query.substring(0, at) + insertions.charAt(random.nextInt(insertions.length())) + query.substring(at);
        }
    }

    private <T> T pick(T[] values) {
        return values[random.nextInt(values.length)];
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
