package io.github.treetrail.jsonpath;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import com.code_intelligence.jazzer.mutation.annotation.WithUtf8Length;
import io.github.treetrail.jsonpath.internal.IRegexp;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Fuzz targets for everything that takes untrusted input: expressions, documents and regular
 * expressions. Each target may only throw the exceptions the API documents, and every input must be
 * processed within {@link #TIME_LIMIT_NANOS}.
 *
 * <p>{@code ./gradlew test} replays the seeds (the Compliance Test Suite) and saved findings.
 * {@code ./gradlew fuzz} fuzzes each target in turn, {@code -PfuzzDuration=10m} sets the time per target.
 */
class JsonPathFuzzTest {

    /** Far above any honest input of the sizes below, far below any exponential blow-up. */
    private static final long TIME_LIMIT_NANOS = 5_000_000_000L;

    /** Keeps the work of fuzzed queries small so that each input finishes quickly. */
    private static final EvaluationLimits LIMITS = EvaluationLimits.DEFAULT.withMaxVisitedNodes(1_000_000);

    @MethodSource("expressions")
    @FuzzTest
    void compileAnyExpression(@NotNull @WithUtf8Length(max = 1_000) String expression) {
        long start = System.nanoTime();
        try {
            JsonPath.compile(expression);
        } catch (JsonPathSyntaxException expected) {
            // the only exception compile() may throw
        }
        checkTime(start, expression);
    }

    @MethodSource("queries")
    @FuzzTest
    void queryAnyDocument(@NotNull @WithUtf8Length(max = 500) String expression,
            @NotNull @WithUtf8Length(max = 5_000) String json) {
        long start = System.nanoTime();
        try {
            JsonPath.compile(expression).withLimits(LIMITS).queryJson(json).paths();
        } catch (JsonPathSyntaxException | InvalidJsonException | JsonPathEvaluationException expected) {
            // documented: invalid expression, invalid JSON, limits exceeded
        }
        checkTime(start, expression + " on " + json);
    }

    @MethodSource("regularExpressions")
    @FuzzTest
    void matchAnyRegularExpression(@NotNull @WithUtf8Length(max = 500) String pattern,
            @NotNull @WithUtf8Length(max = 5_000) String subject) {
        long start = System.nanoTime();
        Optional<IRegexp> regexp = IRegexp.compile(pattern);
        if (regexp.isPresent()) {
            regexp.get().matches(subject);
            regexp.get().find(subject);
        }
        checkTime(start, pattern + " on " + subject);
    }

    @MethodSource("documents")
    @FuzzTest
    void parseAnyJson(@NotNull @WithUtf8Length(max = 10_000) String json) {
        long start = System.nanoTime();
        try {
            Object document = JavaObjectModel.parse(json);
            JavaObjectModel.jsonEquals(document, JavaObjectModel.parse(json));
        } catch (InvalidJsonException expected) {
            // the only exception parse() may throw
        }
        checkTime(start, json);
    }

    private static void checkTime(long start, String input) {
        long elapsed = System.nanoTime() - start;
        if (elapsed > TIME_LIMIT_NANOS) {
            throw new AssertionError("Took " + elapsed / 1_000_000 + " ms: " + abbreviate(input));
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }

    // ---- seeds: the Compliance Test Suite ----

    static Stream<Arguments> expressions() {
        return ComplianceSuite.cases().stream().map(test -> Arguments.of(test.get("selector")));
    }

    static Stream<Arguments> queries() {
        return validCases().map(test -> Arguments.of(test.get("selector"), json(test.get("document"))));
    }

    static Stream<Arguments> documents() {
        return validCases().map(test -> Arguments.of(json(test.get("document"))));
    }

    static Stream<Arguments> regularExpressions() {
        return Stream.of(
                Arguments.of("a.c", "abc"),
                Arguments.of("[a-z]+[0-9]{2,4}", "abc123"),
                Arguments.of("\\p{Lu}\\p{Ll}*", "Hello"),
                Arguments.of("[^\\n\\r]*", "line"),
                Arguments.of("(a|b)*a(a|b){12}", "abababababababab"),
                Arguments.of("((a+)+)+b", "aaaaaaaaaaaaaaaaaaaaaaaa!"),
                Arguments.of("^ab|cd$", "abcd"),
                Arguments.of("[\\p{L}\\p{Nd}_-]{1,64}", "user_42"),
                Arguments.of("(x{2,3}|y?)*z", "xxyxxxz"));
    }

    private static Stream<Map<String, Object>> validCases() {
        return ComplianceSuite.cases().stream().filter(test -> !Boolean.TRUE.equals(test.get("invalid_selector")));
    }

    private static String json(Object value) {
        return TestJson.write(value);
    }
}
