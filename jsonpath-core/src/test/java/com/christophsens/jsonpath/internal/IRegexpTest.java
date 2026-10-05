package com.christophsens.jsonpath.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IRegexpTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "", "abc", "a|b", "(ab)*", "a{2}", "a{2,}", "a{2,3}", "[a-z]", "[^a-z]", "[-a]", "[a-]",
        "\\p{Lu}", "\\P{L}", "[\\p{Nd}x]", "\\.", "\\n", "[\\]]", ".", "^a$", "()", "a||b"
    })
    void acceptsValidExpressions(String regexp) {
        assertThat(IRegexp.compile(regexp)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "(", ")", "a**", "a{3,2}", "a{", "[]", "[^]", "[b-a]", "[a-c-e]", "\\d", "\\w", "\\s",
        "\\p{IsBasicLatin}", "\\p{Cs}", "(?:a)", "a+?", "\\b", "[a-\\p{L}]", "{1}", "a{99999999999}"
    })
    void rejectsExpressionsOutsideIRegexp(String regexp) {
        assertThat(IRegexp.compile(regexp)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "a.c    ; abc   ; true",
        "a.c    ; a\\nc ; false",
        "[a-c]+ ; abcab ; true",
        "[^a-c] ; d     ; true",
        "[^a-c] ; b     ; false",
        "\\p{Lu}\\p{Ll}* ; Hello ; true",
        "\\P{L}+ ; 123  ; true",
        "[\\P{L}\\P{N}] ; a ; true",
        "a{2,3} ; aaaa  ; false",
        "a{2,}  ; aaaa  ; true",
        "(ab|cd){2} ; abcd ; true",
        "x*     ; ''    ; true",
        // Regular-language semantics: the first pass matches the empty string at '^'.
        // java.util.regex answers false here; RE2 and Rust's regex answer true.
        "(^|a){2} ; a  ; true",
    })
    void matchesWholeInput(String regexp, String input, boolean expected) {
        String subject = input.equals("''") ? "" : input.replace("\\n", "\n");
        assertThat(IRegexp.compile(regexp).orElseThrow().matches(subject)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", " ", "\u0085", "😀"})
    void dotMatchesEverythingButLineFeedAndCarriageReturn(String subject) {
        assertThat(IRegexp.compile(".").orElseThrow().matches(subject)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r"})
    void dotDoesNotMatchLineFeedOrCarriageReturn(String subject) {
        assertThat(IRegexp.compile(".").orElseThrow().matches(subject)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "+", "?", "(", ")", "|", "{", "}", "^", ".", "-", "[", "]", "\\"})
    void treatsMetacharactersLiterallyWhenEscaped(String c) {
        assertThat(IRegexp.compile("\\" + c).orElseThrow().matches(c)).isTrue();
    }

    @Test
    void searchFindsSubstrings() {
        IRegexp regexp = IRegexp.compile("b+").orElseThrow();

        assertThat(regexp.find("abbbc")).isTrue();
        assertThat(regexp.find("ac")).isFalse();
        assertThat(IRegexp.compile("^b").orElseThrow().find("ab")).isFalse();
        assertThat(IRegexp.compile("b$").orElseThrow().find("ab")).isTrue();
    }

    @Test
    void matchesSupplementaryCharactersAsOneCodePoint() {
        assertThat(IRegexp.compile("a.b").orElseThrow().matches("a😀b")).isTrue();
        assertThat(IRegexp.compile("[😀-🙏]").orElseThrow().matches("😃")).isTrue();
    }

    @Test
    void evilPatternsRunInLinearTime() {
        String input = "a".repeat(100_000) + "!";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThat(IRegexp.compile("(a*)*b").orElseThrow().matches(input)).isFalse();
            assertThat(IRegexp.compile("(a|a)*b").orElseThrow().matches(input)).isFalse();
            assertThat(IRegexp.compile("(a|aa)+b").orElseThrow().find(input)).isFalse();
            assertThat(IRegexp.compile("(.*a){20}").orElseThrow().matches(input)).isFalse();
        });
    }

    @Test
    void rejectsExpressionsThatExpandTooMuch() {
        assertThat(IRegexp.compile("a{1000}")).isPresent();
        assertThat(IRegexp.compile("(a{1000}){1000}")).isEmpty();
    }

    @Test
    void rejectsDeeplyNestedGroups() {
        assertThat(IRegexp.compile("(".repeat(10_000) + "a" + ")".repeat(10_000))).isEmpty();
    }

    @Test
    void fallsBackToTheSimulationWhenTheDfaGrowsTooLarge() {
        // The deterministic automaton for "the 13th character from the end is an a" needs 2^13 states.
        IRegexp regexp = IRegexp.compile("(a|b)*a(a|b){12}").orElseThrow();
        Pattern java = Pattern.compile("(?:a|b)*a(?:a|b){12}");
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            StringBuilder input = new StringBuilder();
            for (int j = 0; j < 40; j++) {
                input.append(random.nextBoolean() ? 'a' : 'b');
            }
            assertThat(regexp.matches(input.toString())).isEqualTo(java.matcher(input).matches());
        }
    }

    @Test
    void canBeSharedBetweenThreads() {
        IRegexp regexp = IRegexp.compile("[a-c]+x?(d|e)*\\p{Lu}").orElseThrow();
        Pattern java = Pattern.compile("[a-c]+x?(?:d|e)*\\p{Lu}");
        java.util.List<String> inputs = new java.util.ArrayList<>();
        Random random = new Random(2);
        for (int i = 0; i < 5_000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = random.nextInt(12); j > 0; j--) {
                sb.append("abcxdeQ\u00c4\uD83D\uDE00".charAt(random.nextInt(9)));
            }
            inputs.add(sb.toString());
        }
        assertThat(inputs.parallelStream().filter(regexp::matches).count())
                .isEqualTo(inputs.stream().filter(s -> java.matcher(s).matches()).count());
    }

    /** Compares the automaton with java.util.regex on random expressions and inputs. */
    @Test
    void agreesWithJavaRegexOnRandomExpressions() {
        Random random = new Random(9535);
        for (int i = 0; i < 3_000; i++) {
            StringBuilder iregexp = new StringBuilder();
            StringBuilder java = new StringBuilder();
            randomRegexp(random, 3, iregexp, java);
            IRegexp ours = IRegexp.compile(iregexp.toString()).orElseThrow(
                    () -> new AssertionError("rejected " + iregexp));
            Pattern theirs = Pattern.compile(java.toString());
            for (int j = 0; j < 20; j++) {
                String input = randomInput(random);
                assertThat(ours.matches(input))
                        .as("match %s on %s", iregexp, input)
                        .isEqualTo(theirs.matcher(input).matches());
                assertThat(ours.find(input))
                        .as("search %s on %s", iregexp, input)
                        .isEqualTo(theirs.matcher(input).find());
            }
        }
    }

    private static void randomRegexp(Random random, int depth, StringBuilder iregexp, StringBuilder java) {
        int pieces = 1 + random.nextInt(3);
        for (int p = 0; p < pieces; p++) {
            // Anchors only at the top level: java.util.regex mishandles empty loop passes over anchors.
            int kind = random.nextInt(depth > 0 ? 9 : 5);
            if (kind == 5 && depth < 3) {
                kind = 0;
            }
            switch (kind) {
                case 0: case 1:
                    String c = random.nextBoolean() ? "a" : "b";
                    iregexp.append(c);
                    java.append(c);
                    break;
                case 2:
                    iregexp.append('.');
                    java.append("[^\\n\\r]");
                    break;
                case 3:
                    iregexp.append("[ab]");
                    java.append("[ab]");
                    break;
                case 4:
                    iregexp.append("[^a]");
                    java.append("[^a]");
                    break;
                case 5:
                    boolean begin = random.nextBoolean();
                    iregexp.append(begin ? "^" : "$");
                    java.append(begin ? "\\A" : "\\z");
                    continue;
                default:
                    iregexp.append('(');
                    java.append("(?:");
                    randomRegexp(random, depth - 1, iregexp, java);
                    if (random.nextInt(3) == 0) {
                        iregexp.append('|');
                        java.append('|');
                        randomRegexp(random, depth - 1, iregexp, java);
                    }
                    iregexp.append(')');
                    java.append(')');
            }
            String quantifier = new String[] {"", "", "*", "+", "?", "{2}", "{1,3}", "{0,}"}[random.nextInt(8)];
            iregexp.append(quantifier);
            java.append(quantifier);
        }
    }

    private static String randomInput(Random random) {
        StringBuilder sb = new StringBuilder();
        int length = random.nextInt(9);
        for (int i = 0; i < length; i++) {
            sb.append("aabbc\n".charAt(random.nextInt(6)));
        }
        return sb.toString();
    }
}
