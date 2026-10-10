package io.github.treetrail.jsonpath.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.treetrail.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The per-call cache of patterns that queries read from documents (issue #12). */
class RegexCacheTest {

    @Test
    void returnsTheSameExpressionForTheSamePattern() {
        RegexCache cache = new RegexCache();

        assertThat(cache.get("a+").orElseThrow()).isSameAs(cache.get("a+").orElseThrow());
        assertThat(cache.get("(")).isEmpty();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void boundsTheNumberOfExpressions() {
        RegexCache cache = new RegexCache();
        for (int n = 0; n < 1_000; n++) {
            assertThat(cache.get("a{" + n + "}").orElseThrow().matches("a".repeat(n)))
                    .isTrue();
            assertThat(cache.size()).isLessThanOrEqualTo(RegexCache.MAX_ENTRIES);
        }
    }

    @Test
    void boundsTheInstructionsOfTheExpressions() {
        RegexCache cache = new RegexCache();
        for (int n = 0; n < 60; n++) {
            assertThat(cache.get("a{5000}x{0," + n + "}")).isPresent();
            assertThat(cache.instructions()).isLessThanOrEqualTo(RegexCache.MAX_INSTRUCTIONS);
        }
        assertThat(cache.get("a{5000}").orElseThrow().matches("a".repeat(5000))).isTrue();
    }

    @Test
    void returnsTheMemoryOfEvictedAutomata() {
        RegexCache cache = new RegexCache();
        IRegexp evicted = cache.get("(a|b)*a(a|b){8}").orElseThrow();
        evicted.matches("ab".repeat(50));
        long automaton = evicted.automatonBytes(false);
        int generation = IRegexp.dfaGeneration();
        long before = IRegexp.cachedDfaBytes();

        for (int n = 0; n < RegexCache.MAX_ENTRIES; n++) {
            cache.get("x{" + n + "}");
        }

        assertThat(automaton).isPositive();
        assertThat(evicted.automatonBytes(false)).isZero();
        assertThat(IRegexp.dfaGeneration()).isEqualTo(generation);
        assertThat(IRegexp.cachedDfaBytes()).isEqualTo(before - automaton);
    }

    @Test
    void eachCallOfAQueryHasItsOwnCache() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int n = 0; n < 500; n++) {
            items.add(Map.of("s", "a".repeat(n % 7), "p", "a{" + n % 7 + "}", "q", "a{0," + n + "}"));
        }
        JsonPath path = JsonPath.compile("$[?match(@.s, @.p) && search(@.s, @.q)]");

        assertThat(path.query(items)).hasSize(500);
    }
}
