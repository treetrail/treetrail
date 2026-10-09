package io.github.treetrail.jsonpath.migration;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of running one expression with Jayway JsonPath and with this library.
 *
 * @param expression the expression that was compared
 * @param outcome how the two results relate
 * @param rfcValues the values selected under RFC 9535, or {@code null} if the expression is not valid RFC 9535
 * @param jaywayValues the values Jayway returned, as a list, or {@code null} if Jayway failed
 * @param jaywayReturnsSingleValue whether Jayway returned a single value rather than a list; code that
 *     reads such results must switch to {@code NodeList.single()} or {@code values()}
 * @param detail the error message or migration hint, or {@code null}
 */
public record Comparison(
        String expression,
        Outcome outcome,
        @Nullable List<@Nullable Object> rfcValues,
        @Nullable List<@Nullable Object> jaywayValues,
        boolean jaywayReturnsSingleValue,
        @Nullable String detail) {

    /** How the result under RFC 9535 relates to the result of Jayway JsonPath. */
    public enum Outcome {
        /** Both select the same values in the same order. */
        SAME,
        /** Both select the same values, but in a different order. */
        SAME_VALUES_DIFFERENT_ORDER,
        /** Both accept the expression but select different values. */
        DIFFERENT_VALUES,
        /** Jayway accepts the expression; it is not valid RFC 9535 and needs rewriting. */
        ONLY_JAYWAY_ACCEPTS,
        /** The expression is valid RFC 9535, but Jayway rejects it. */
        ONLY_RFC_ACCEPTS,
        /** Jayway accepts the expression but fails while evaluating it. */
        JAYWAY_FAILS_AT_RUNTIME,
        /** Neither accepts the expression. */
        BOTH_REJECT
    }

    /** Whether the expression can be migrated as is: same values, in the same order. */
    public boolean isSame() {
        return outcome == Outcome.SAME;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(outcome.name()).append("  ").append(expression);
        if (outcome != Outcome.SAME) {
            sb.append("\n    RFC 9535: ").append(rfcValues == null ? "invalid" : rfcValues);
            sb.append("\n    Jayway:   ").append(jaywayValues == null ? "error" : jaywayValues);
        }
        if (jaywayReturnsSingleValue) {
            sb.append("\n    note: Jayway returns a single value here; RFC 9535 always returns a node list");
        }
        if (detail != null) {
            sb.append("\n    ").append(detail);
        }
        return sb.toString();
    }
}
