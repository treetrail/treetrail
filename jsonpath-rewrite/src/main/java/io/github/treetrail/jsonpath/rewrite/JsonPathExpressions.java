package io.github.treetrail.jsonpath.rewrite;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

/**
 * Every JSONPath expression found by {@link FindJaywayJsonPathExpressions}, with its RFC 9535 assessment.
 */
public class JsonPathExpressions extends DataTable<JsonPathExpressions.Row> {

    /**
     * Creates the data table.
     *
     * @param recipe the recipe that fills it
     */
    public JsonPathExpressions(Recipe recipe) {
        super(
                recipe,
                "JSONPath expressions",
                "Jayway JsonPath and Spring jsonPath() expressions and how they relate to RFC 9535.");
    }

    /** One expression at one call site. */
    public static final class Row {

        @Column(displayName = "Source path", description = "The file that contains the call.")
        private final String sourcePath;

        @Column(displayName = "Call", description = "The method that receives the expression.")
        private final String call;

        @Column(displayName = "Expression", description = "The JSONPath expression, if it is a string literal.")
        private final @Nullable String expression;

        @Column(
                displayName = "Assessment",
                description = "VALID, VALID_SINGLE_VALUE, NOT_RFC_9535, " + "WRITE_API or NOT_A_LITERAL.")
        private final String assessment;

        @Column(displayName = "Hint", description = "What to do when migrating to RFC 9535.")
        private final String hint;

        /**
         * Creates a row.
         *
         * @param sourcePath the file that contains the call
         * @param call the method that receives the expression
         * @param expression the expression, or {@code null} if it is not known when the code is compiled
         * @param assessment {@code VALID}, {@code VALID_SINGLE_VALUE}, {@code NOT_RFC_9535}, {@code WRITE_API} or
         *     {@code NOT_A_LITERAL}
         * @param hint what to do when migrating to RFC 9535
         */
        public Row(String sourcePath, String call, @Nullable String expression, String assessment, String hint) {
            this.sourcePath = sourcePath;
            this.call = call;
            this.expression = expression;
            this.assessment = assessment;
            this.hint = hint;
        }

        /** Returns the file that contains the call. */
        public String getSourcePath() {
            return sourcePath;
        }

        /** Returns the method that receives the expression. */
        public String getCall() {
            return call;
        }

        /** Returns the expression, or {@code null} if it is not known when the code is compiled. */
        public @Nullable String getExpression() {
            return expression;
        }

        /** Returns the assessment, for example {@code VALID} or {@code NOT_RFC_9535}. */
        public String getAssessment() {
            return assessment;
        }

        /** Returns what to do when migrating to RFC 9535. */
        public String getHint() {
            return hint;
        }
    }
}
