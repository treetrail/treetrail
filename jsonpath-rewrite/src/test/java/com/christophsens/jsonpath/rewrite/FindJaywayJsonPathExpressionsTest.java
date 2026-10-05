package com.christophsens.jsonpath.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class FindJaywayJsonPathExpressionsTest implements RewriteTest {

    private static final String SPRING_STUB = """
            package org.springframework.test.web.servlet.result;
            public class MockMvcResultMatchers {
                public static Object jsonPath(String expression, Object... args) { return null; }
            }
            """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJaywayJsonPathExpressions())
                .parser(JavaParser.fromJavaVersion().classpath("json-path").dependsOn(SPRING_STUB));
    }

    @Test
    void assessesJaywayReads() {
        rewriteRun(
                spec -> spec.dataTable(JsonPathExpressions.Row.class, rows -> {
                    assertThat(rows).extracting(JsonPathExpressions.Row::getAssessment)
                            .containsExactly("VALID", "VALID_SINGLE_VALUE", "NOT_RFC_9535");
                    assertThat(rows).extracting(JsonPathExpressions.Row::getExpression)
                            .containsExactly("$.store.book[?(@.price < 10)].title", "$.store.bicycle.color",
                                    "$.store.book.length()");
                }),
                java(
                        """
                        import com.jayway.jsonpath.JsonPath;

                        class Books {
                            Object cheap(String json) {
                                return JsonPath.read(json, "$.store.book[?(@.price < 10)].title");
                            }
                            Object color(String json) {
                                return JsonPath.parse(json).read("$.store.bicycle.color");
                            }
                            Object count(String json) {
                                return JsonPath.read(json, "$.store.book.length()");
                            }
                        }
                        """,
                        """
                        import com.jayway.jsonpath.JsonPath;

                        class Books {
                            Object cheap(String json) {
                                return /*~~(VALID: Valid RFC 9535. Results can still differ (slices, type coercion); check with JaywayComparison.)~~>*/JsonPath.read(json, "$.store.book[?(@.price < 10)].title");
                            }
                            Object color(String json) {
                                return /*~~(VALID_SINGLE_VALUE: Valid RFC 9535. Jayway returns a single value here, RFC 9535 a node list: use NodeList.single().)~~>*/JsonPath.parse(json).read("$.store.bicycle.color");
                            }
                            Object count(String json) {
                                return /*~~(NOT_RFC_9535: Path functions like .length() are not part of RFC 9535. Take the size of the result in Java (values().size()), or filter with length(), e.g. $[?length(@.tags) > 2].)~~>*/JsonPath.read(json, "$.store.book.length()");
                            }
                        }
                        """));
    }

    @Test
    void flagsWritesAndComputedExpressions() {
        rewriteRun(
                java(
                        """
                        import com.jayway.jsonpath.JsonPath;

                        class Writes {
                            void update(String json, String path) {
                                JsonPath.parse(json).set("$.store.bicycle.color", "blue");
                                JsonPath.compile(path);
                            }
                        }
                        """,
                        """
                        import com.jayway.jsonpath.JsonPath;

                        class Writes {
                            void update(String json, String path) {
                                /*~~(WRITE_API: RFC 9535 defines queries only; select the nodes and modify the document with your JSON library.)~~>*/JsonPath.parse(json).set("$.store.bicycle.color", "blue");
                                /*~~(NOT_A_LITERAL: The expression is computed at runtime; check it with JaywayComparison.)~~>*/JsonPath.compile(path);
                            }
                        }
                        """));
    }

    @Test
    void assessesSpringMockMvcExpressions() {
        rewriteRun(
                java(
                        """
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            Object check() {
                                return jsonPath("$.items[?(@.name =~ /a.*/)]");
                            }
                        }
                        """,
                        """
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            Object check() {
                                return /*~~(NOT_RFC_9535: Replace =~ /regex/ with match(@.x, 'regex') for a full match or search(@.x, 'regex') for a substring. RFC 9535 uses I-Regexp: no \\d, \\w, \\s or flags like /i.)~~>*/jsonPath("$.items[?(@.name =~ /a.*/)]");
                            }
                        }
                        """));
    }

    @Test
    void leavesUnrelatedCodeAlone() {
        rewriteRun(
                java(
                        """
                        class Plain {
                            String read(String a, String b) {
                                return a + b;
                            }
                        }
                        """));
    }
}
