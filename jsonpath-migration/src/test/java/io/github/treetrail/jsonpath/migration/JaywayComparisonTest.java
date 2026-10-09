package io.github.treetrail.jsonpath.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import io.github.treetrail.jsonpath.migration.Comparison.Outcome;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JaywayComparisonTest {

    private static final Object STORE = parse("""
            {"store": {
              "book": [
                {"title": "Sayings of the Century", "price": 8.95, "tags": ["a", "b"]},
                {"title": "Sword of Honour", "price": 12.99, "tags": []},
                {"title": "Moby Dick", "price": 8.99, "isbn": "0-553-21311-3", "tags": ["c"]}
              ],
              "bicycle": {"color": "red", "price": 399}
            }}""");

    private final JaywayComparison comparison = JaywayComparison.withDefaults();

    @Test
    void sameResultForTypicalFilter() {
        Comparison c = comparison.compare("$.store.book[?(@.price < 10)].title", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.SAME);
        assertThat(c.rfcValues()).containsExactly("Sayings of the Century", "Moby Dick");
        assertThat(c.jaywayReturnsSingleValue()).isFalse();
    }

    @Test
    void flagsDefinitePathsWhereJaywayReturnsASingleValue() {
        Comparison c = comparison.compare("$.store.bicycle.color", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.SAME);
        assertThat(c.jaywayReturnsSingleValue()).isTrue();
    }

    @Test
    void treatsJaywaysPathNotFoundAsAnEmptyResult() {
        assertThat(comparison.compare("$.store.missing", STORE).outcome()).isEqualTo(Outcome.SAME);
    }

    @Test
    void respectsTheApplicationsJaywayOptions() {
        JaywayComparison withLeafToNull = JaywayComparison.with(
                Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL));

        Comparison c = withLeafToNull.compare("$.store.bicycle.size", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.DIFFERENT_VALUES);
        assertThat(c.rfcValues()).isEmpty();
        assertThat(c.jaywayValues()).containsExactly((Object) null);
    }

    @Test
    void explainsPathFunctions() {
        Comparison c = comparison.compare("$.store.book.length()", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.ONLY_JAYWAY_ACCEPTS);
        assertThat(c.detail()).contains("length()");
    }

    @Test
    void explainsRegexOperator() {
        Comparison c = comparison.compare("$.store.book[?(@.title =~ /M.*/)].title", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.ONLY_JAYWAY_ACCEPTS);
        assertThat(c.detail()).contains("match(");
    }

    @Test
    void explainsSetOperators() {
        Comparison c = comparison.compare("$.store.book[?(@.title in ['Moby Dick'])].price", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.ONLY_JAYWAY_ACCEPTS);
        assertThat(c.detail()).contains("||");
    }

    @Test
    void reportsRfcSyntaxJaywayDoesNotKnow() {
        Comparison c = comparison.compare("$.store.book[?length(@.tags) > 1].title", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.ONLY_RFC_ACCEPTS);
        assertThat(c.rfcValues()).containsExactly("Sayings of the Century");
    }

    @Test
    void reportsDifferentValues() {
        Comparison c = comparison.compare("$.store.book[0:3:2].title", STORE);

        assertThat(c.outcome()).isEqualTo(Outcome.DIFFERENT_VALUES);
        assertThat(c.rfcValues()).containsExactly("Sayings of the Century", "Moby Dick");
    }

    @Test
    void reportsExpressionsBothReject() {
        assertThat(comparison.compare("", STORE).outcome()).isEqualTo(Outcome.BOTH_REJECT);
    }

    @Test
    void flagsExpressionsOnlyJaywaysLenientParserAccepts() {
        // Jayway accepts an unterminated bracket; RFC 9535 rejects it.
        assertThat(comparison.compare("$.store[", STORE).outcome()).isEqualTo(Outcome.ONLY_JAYWAY_ACCEPTS);
    }

    @Test
    void summarizesSeveralExpressions() {
        MigrationReport report = comparison.compareAll(
                List.of("$.store.book[*].title", "$.store.book.length()", "$.store.book[0:3:2].title"), STORE);

        assertThat(report.counts())
                .containsEntry(Outcome.SAME, 1)
                .containsEntry(Outcome.ONLY_JAYWAY_ACCEPTS, 1)
                .containsEntry(Outcome.DIFFERENT_VALUES, 1);
        assertThat(report.differences()).hasSize(2);
        assertThat(report.toString()).contains("3 expressions compared", "$.store.book.length()");
    }

    @Test
    void comparesAgainstJsonTextParsedByJayway() {
        Comparison c = comparison.compareJson("$[?(@.n == 1)]", "[{\"n\":1},{\"n\":1.0},{\"n\":\"1\"}]");

        assertThat(c.outcome()).isEqualTo(Outcome.DIFFERENT_VALUES);
        assertThat(c.rfcValues()).hasSize(2);
        assertThat(c.jaywayValues()).hasSize(3);
    }

    @Test
    void rejectsPathListOption() {
        Configuration pathList = Configuration.defaultConfiguration().addOptions(Option.AS_PATH_LIST);

        assertThatThrownBy(() -> JaywayComparison.with(pathList)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Object parse(String json) {
        try {
            return new ObjectMapper().readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
