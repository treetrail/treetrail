package io.github.treetrail.jsonpath.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FilterParenthesesTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "$[?@.a]                     ; $[?(@.a)]",
        "$[?@.a == 1, 0]             ; $[?(@.a == 1), 0]",
        "$[?match(@.a, 'x]')]        ; $[?(match(@.a, 'x]'))]",
        "$[?@[?@.b]]                 ; $[?(@[?(@.b)])]",
        "$..x[?@.a && (@.b || @.c)]  ; $..x[?(@.a && (@.b || @.c))]",
        "$.a[0]                      ; $.a[0]",
    })
    void wrapsEveryFilterExpression(String selector, String expected) {
        assertThat(JaywayConformanceReport.withFilterParentheses(selector)).isEqualTo(expected);
    }
}
