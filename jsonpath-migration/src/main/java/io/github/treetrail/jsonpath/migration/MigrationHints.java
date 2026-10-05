package io.github.treetrail.jsonpath.migration;

import java.util.regex.Pattern;

/**
 * Suggestions for rewriting Jayway-only expressions in RFC 9535 syntax.
 */
public final class MigrationHints {

    private static final Pattern PATH_FUNCTION = Pattern.compile(
            "\\.(min|max|avg|stddev|sum|first|last|index|keys|concat|append)\\(.*\\)\\s*$");
    private static final Pattern LENGTH_FUNCTION = Pattern.compile("\\.(length|size)\\(\\)\\s*$");
    private static final Pattern REGEX_OPERATOR = Pattern.compile("=~");
    private static final Pattern SET_OPERATOR = Pattern.compile("\\s(in|nin|subsetof|anyof|noneof)\\s");
    private static final Pattern SIZE_OPERATOR = Pattern.compile("\\s(size|empty)\\s");

    private MigrationHints() {
    }

    /**
     * Returns a rewrite hint for an expression that is not valid RFC 9535.
     *
     * @param expression the Jayway expression
     * @param rfcError the parser message, used when no specific hint applies
     */
    public static String forInvalidExpression(String expression, String rfcError) {
        if (LENGTH_FUNCTION.matcher(expression).find()) {
            return "Path functions like .length() are not part of RFC 9535. Take the size of the result "
                    + "in Java (values().size()), or filter with length(), e.g. $[?length(@.tags) > 2].";
        }
        if (PATH_FUNCTION.matcher(expression).find()) {
            return "Aggregate functions like .min(), .max(), .sum() are not part of RFC 9535. "
                    + "Select the values and aggregate them in Java.";
        }
        if (REGEX_OPERATOR.matcher(expression).find()) {
            return "Replace =~ /regex/ with match(@.x, 'regex') for a full match or search(@.x, 'regex') "
                    + "for a substring. RFC 9535 uses I-Regexp: no \\d, \\w, \\s or flags like /i.";
        }
        if (SET_OPERATOR.matcher(expression).find()) {
            return "Set operators (in, nin, subsetof, anyof, noneof) are not part of RFC 9535. "
                    + "Combine comparisons instead, e.g. [?@.x == 'a' || @.x == 'b'].";
        }
        if (SIZE_OPERATOR.matcher(expression).find()) {
            return "The size and empty operators are not part of RFC 9535. Use length(), "
                    + "e.g. [?length(@.tags) == 2] or [?length(@.tags) == 0].";
        }
        return "Not valid RFC 9535: " + rfcError;
    }
}
