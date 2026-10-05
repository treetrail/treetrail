package com.christophsens.jsonpath.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IRegexpTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "", "abc", "a|b", "(ab)*", "a{2}", "a{2,}", "a{2,3}", "[a-z]", "[^a-z]", "[-a]", "[a-]",
        "\\p{Lu}", "\\P{L}", "[\\p{Nd}x]", "\\.", "\\n", "[\\]]", ".", "^a$"
    })
    void acceptsValidExpressions(String regexp) {
        assertThat(IRegexp.compile(regexp)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "(", ")", "a**", "a{3,2}", "a{", "[]", "[^]", "[b-a]", "[a-c-e]", "\\d", "\\w", "\\s",
        "\\p{IsBasicLatin}", "\\p{Cs}", "(?:a)", "a+?", "\\b", "[a-\\p{L}]", "{1}"
    })
    void rejectsExpressionsOutsideIRegexp(String regexp) {
        assertThat(IRegexp.compile(regexp)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", " ", "\u0085", "😀"})
    void dotMatchesEverythingButLineFeedAndCarriageReturn(String subject) {
        assertThat(IRegexp.compile(".").orElseThrow().matcher(subject).matches()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r"})
    void dotDoesNotMatchLineFeedOrCarriageReturn(String subject) {
        assertThat(IRegexp.compile(".").orElseThrow().matcher(subject).matches()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "+", "?", "(", ")", "|", "{", "}"})
    void treatsJavaMetacharactersLiterallyWhenEscaped(String c) {
        assertThat(IRegexp.compile("\\" + c).orElseThrow().matcher(c).matches()).isTrue();
    }
}
