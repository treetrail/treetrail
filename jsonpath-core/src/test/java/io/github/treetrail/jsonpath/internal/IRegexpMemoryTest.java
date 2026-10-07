package io.github.treetrail.jsonpath.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Memory bounds of the regex caches (issue #41). Expressions can come from documents, so a query
 * over untrusted data must not be able to make the caches retain unbounded memory.
 */
class IRegexpMemoryTest {

    /** Needs 2^13 deterministic states; the suffix makes each pattern a distinct cache entry. */
    private static String explosive(int n) {
        return "(a|b)*a(a|b){12}c{0," + n + "}";
    }

    private static Pattern java(int n) {
        return Pattern.compile("(?:a|b)*a(?:a|b){12}c{0," + n + "}");
    }

    private static String randomAb(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextBoolean() ? 'a' : 'b');
        }
        return sb.toString();
    }

    @Test
    void boundsTheMemoryOfManyDocumentSourcedPatterns() {
        int generationBefore = IRegexp.dfaGeneration();
        Random random = new Random(41);
        for (int n = 0; n < 256; n++) {
            IRegexp regexp = IRegexp.compile(explosive(n)).orElseThrow();
            Pattern java = java(n);
            for (int i = 0; i < 400; i++) {
                String input = randomAb(random, 60);
                boolean matches = regexp.matches(input);
                boolean found = regexp.find(input);
                if (i % 20 == 0) {
                    assertThat(matches).as("match %s on %s", n, input).isEqualTo(java.matcher(input).matches());
                    assertThat(found).as("search %s on %s", n, input).isEqualTo(java.matcher(input).find());
                }
            }
            assertThat(IRegexp.cachedDfaBytes()).isLessThanOrEqualTo(IRegexp.MAX_DFA_BYTES);
            assertThat(IRegexp.cachedInstructions()).isLessThanOrEqualTo(IRegexp.MAX_CACHED_INSTRUCTIONS);
        }
        // The scenario retained 818 MB before the fix; now it has to run into the global bound.
        assertThat(IRegexp.dfaGeneration()).isGreaterThan(generationBefore);
    }

    @Test
    void boundsOneAutomaton() {
        IRegexp regexp = IRegexp.compile("(a|b)*a(a|b){12}").orElseThrow();
        Pattern java = Pattern.compile("(?:a|b)*a(?:a|b){12}");
        Random random = new Random(1);
        for (int i = 0; i < 5_000; i++) {
            String input = randomAb(random, 80);
            assertThat(regexp.matches(input)).isEqualTo(java.matcher(input).matches());
            assertThat(regexp.automatonBytes(false)).isLessThanOrEqualTo(IRegexp.MAX_DFA_BYTES_PER_AUTOMATON);
        }
    }

    @Test
    void boundsTransitionsOnNonAsciiCodePoints() {
        // Every distinct code point outside ASCII gets its own cached transition.
        IRegexp regexp = IRegexp.compile("\\p{Lo}*x").orElseThrow();
        StringBuilder input = new StringBuilder();
        for (int cp = 0x4E00; cp < 0x4E00 + 20_000; cp++) {
            input.appendCodePoint(cp);
        }
        assertThat(regexp.find(input.toString())).isFalse();
        assertThat(regexp.matches(input + "x")).isTrue();
        assertThat(regexp.automatonBytes(true)).isLessThanOrEqualTo(IRegexp.MAX_DFA_BYTES_PER_AUTOMATON);
        assertThat(regexp.automatonBytes(false)).isLessThanOrEqualTo(IRegexp.MAX_DFA_BYTES_PER_AUTOMATON);
    }

    @Test
    void boundsTheInstructionsOfCachedExpressions() {
        for (int n = 0; n < 60; n++) {
            assertThat(IRegexp.compile("a{5000}x{0," + n + "}")).isPresent();
            assertThat(IRegexp.cachedInstructions()).isLessThanOrEqualTo(IRegexp.MAX_CACHED_INSTRUCTIONS);
        }
        assertThat(IRegexp.compile("a{5000}").orElseThrow().matches("a".repeat(5000))).isTrue();
    }

    @Test
    void staysCorrectWhileAutomataAreDiscardedConcurrently() {
        List<String> inputs = new ArrayList<>();
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            inputs.add(randomAb(random, 40));
        }
        AtomicInteger mismatches = new AtomicInteger();
        IntStream.range(0, 128).parallel().forEach(n -> {
            IRegexp regexp = IRegexp.compile(explosive(1_000 + n)).orElseThrow();
            Pattern java = java(1_000 + n);
            for (String input : inputs) {
                if (regexp.matches(input) != java.matcher(input).matches()
                        || regexp.find(input) != java.matcher(input).find()) {
                    mismatches.incrementAndGet();
                }
            }
        });
        assertThat(mismatches).hasValue(0);
        assertThat(IRegexp.cachedDfaBytes()).isLessThanOrEqualTo(IRegexp.MAX_DFA_BYTES);
    }
}
