package io.github.treetrail.jsonpath.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class FindJaywayJsonPathExpressionsTest implements RewriteTest {

    private static final String SPRING_STUB = """
            package org.springframework.test.web.servlet.result;
            public class MockMvcResultMatchers {
                public static Object jsonPath(String expression, Object... args) { return null; }
            }
            """;

    private static final String WEB_TEST_CLIENT_STUB = """
            package org.springframework.test.web.reactive.server;
            public interface WebTestClient {
                interface BodyContentSpec {
                    Object jsonPath(String expression, Object... args);
                }
            }
            """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJaywayJsonPathExpressions())
                .parser(JavaParser.fromJavaVersion()
                        .classpath("json-path")
                        .dependsOn(SPRING_STUB, WEB_TEST_CLIENT_STUB));
    }

    @Test
    void assessesJaywayReads() {
        rewriteRun(
                spec -> spec.dataTable(JsonPathExpressions.Row.class, rows -> {
                    assertThat(rows)
                            .extracting(JsonPathExpressions.Row::getAssessment)
                            .containsExactly("VALID", "VALID_SINGLE_VALUE", "NOT_RFC_9535");
                    assertThat(rows)
                            .extracting(JsonPathExpressions.Row::getExpression)
                            .containsExactly(
                                    "$.store.book[?(@.price < 10)].title",
                                    "$.store.bicycle.color",
                                    "$.store.book.length()");
                }),
                java("""
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
                        """, """
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
        rewriteRun(java("""
                        import com.jayway.jsonpath.JsonPath;

                        class Writes {
                            void update(String json, String path) {
                                JsonPath.parse(json).set("$.store.bicycle.color", "blue");
                                JsonPath.compile(path);
                            }
                        }
                        """, """
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
        rewriteRun(java("""
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            Object check() {
                                return jsonPath("$.items[?(@.name =~ /a.*/)]");
                            }
                        }
                        """, """
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            Object check() {
                                return /*~~(NOT_RFC_9535: Replace =~ /regex/ with match(@.x, 'regex') for a full match or search(@.x, 'regex') for a substring. RFC 9535 uses I-Regexp: no \\d, \\w, \\s or flags like /i.)~~>*/jsonPath("$.items[?(@.name =~ /a.*/)]");
                            }
                        }
                        """));
    }

    @Test
    void assessesFormatTemplatesWithTheirArguments() {
        rewriteRun(
                spec -> spec.dataTable(
                        JsonPathExpressions.Row.class,
                        rows -> assertThat(rows)
                                .extracting(JsonPathExpressions.Row::getExpression)
                                .containsExactly("$.items[%d].name", "$.items[%d].name", "$.items[?(@.name == '%s')]")),
                java("""
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            void check(int index, String name) {
                                jsonPath("$.items[%d].name", 0);
                                jsonPath("$.items[%d].name", index);
                                jsonPath("$.items[?(@.name == '%s')]", name);
                            }
                        }
                        """, """
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            void check(int index, String name) {
                                /*~~(VALID_SINGLE_VALUE: Format template, assessed as $.items[0].name. Valid RFC 9535. Jayway returns a single value here, RFC 9535 a node list: use NodeList.single().)~~>*/jsonPath("$.items[%d].name", 0);
                                /*~~(VALID_SINGLE_VALUE: Format template, assessed as $.items[0].name. Valid RFC 9535. Jayway returns a single value here, RFC 9535 a node list: use NodeList.single().)~~>*/jsonPath("$.items[%d].name", index);
                                /*~~(VALID: Format template, assessed as $.items[?(@.name == 'a')]. Valid RFC 9535. Results can still differ (slices, type coercion); check with JaywayComparison.)~~>*/jsonPath("$.items[?(@.name == '%s')]", name);
                            }
                        }
                        """));
    }

    @Test
    void assessesTheFormattedExpressionOfAnInvalidTemplate() {
        rewriteRun(
                spec -> spec.dataTable(JsonPathExpressions.Row.class, rows -> {
                    assertThat(rows)
                            .extracting(JsonPathExpressions.Row::getExpression)
                            .containsExactly("$.a[%d", "$.b[%q]");
                    assertThat(rows)
                            .extracting(JsonPathExpressions.Row::getAssessment)
                            .containsExactly("NOT_RFC_9535", "NOT_RFC_9535");
                    assertThat(rows.get(0).getHint())
                            .startsWith("Format template, assessed as $.a[1. Not valid RFC 9535");
                    // A template that does not fit its arguments is assessed as written.
                    assertThat(rows.get(1).getHint()).startsWith("Not valid RFC 9535");
                }),
                java("""
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            void check() {
                                jsonPath("$.a[%d", 1);
                                jsonPath("$.b[%q]", 1);
                            }
                        }
                        """, source -> source.after(actual -> actual)));
    }

    @Test
    void resolvesConstantsAcrossFiles() {
        rewriteRun(
                spec -> spec.dataTable(
                        JsonPathExpressions.Row.class,
                        rows -> assertThat(rows)
                                .extracting(JsonPathExpressions.Row::getExpression)
                                .containsExactly(
                                        "$.store.bicycle.color", "$.store.book[*].title", "$.store.book.length()")),
                java("""
                        package paths;

                        public interface Paths {
                            String STORE = "$.store";
                            String TITLES = STORE + ".book[*].title";
                        }
                        """),
                java("""
                        import com.jayway.jsonpath.JsonPath;
                        import paths.Paths;

                        class Store {
                            private static final String COLOR = Paths.STORE + ".bicycle.color";
                            static final String COUNT = "$.store.book.length()";

                            Object read(String json) {
                                JsonPath.read(json, COLOR);
                                JsonPath.read(json, Paths.TITLES);
                                return JsonPath.read(json, COUNT);
                            }
                        }
                        """, """
                        import com.jayway.jsonpath.JsonPath;
                        import paths.Paths;

                        class Store {
                            private static final String COLOR = Paths.STORE + ".bicycle.color";
                            static final String COUNT = "$.store.book.length()";

                            Object read(String json) {
                                /*~~(VALID_SINGLE_VALUE: Valid RFC 9535. Jayway returns a single value here, RFC 9535 a node list: use NodeList.single().)~~>*/JsonPath.read(json, COLOR);
                                /*~~(VALID: Valid RFC 9535. Results can still differ (slices, type coercion); check with JaywayComparison.)~~>*/JsonPath.read(json, Paths.TITLES);
                                return /*~~(NOT_RFC_9535: Path functions like .length() are not part of RFC 9535. Take the size of the result in Java (values().size()), or filter with length(), e.g. $[?length(@.tags) > 2].)~~>*/JsonPath.read(json, COUNT);
                            }
                        }
                        """));
    }

    @Test
    void usesSampleValuesOfTheArgumentTypes() {
        rewriteRun(
                spec -> spec.dataTable(
                        JsonPathExpressions.Row.class,
                        rows -> assertThat(rows)
                                .extracting(JsonPathExpressions.Row::getHint)
                                .allSatisfy(hint -> assertThat(hint).doesNotContain("Not valid"))
                                .extracting(hint -> hint.substring(0, hint.indexOf(". Valid")))
                                .containsExactly(
                                        "Format template, assessed as $.a[?(@.b == 'a' && @.c == 'a')]",
                                        "Format template, assessed as $.a[?(@.b == true && @.c == true)]",
                                        "Format template, assessed as $.a[?(@.b == 0.000000 && @.c == 0.000000)]",
                                        "Format template, assessed as $.a[?(@.b == 0)]")),
                java("""
                        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

                        class ApiTest {
                            void check(char c, Character boxed, boolean flag, Boolean boxedFlag, double d, float f, Object other) {
                                jsonPath("$.a[?(@.b == '%s' && @.c == '%s')]", c, boxed);
                                jsonPath("$.a[?(@.b == %b && @.c == %b)]", flag, boxedFlag);
                                jsonPath("$.a[?(@.b == %f && @.c == %f)]", d, f);
                                jsonPath("$.a[?(@.b == %s)]", other);
                            }
                        }
                        """, source -> source.after(actual -> actual)));
    }

    @Test
    void resolvesOnlyConstantsThatAreKnownAtCompileTime() {
        rewriteRun(
                spec -> spec.dataTable(
                        JsonPathExpressions.Row.class,
                        rows -> assertThat(rows)
                                .extracting(JsonPathExpressions.Row::getAssessment)
                                .containsExactly(
                                        "VALID_SINGLE_VALUE", "NOT_A_LITERAL", "NOT_A_LITERAL", "NOT_A_LITERAL")),
                java("""
                        import com.jayway.jsonpath.JsonPath;

                        class Store {
                            static final String ITEM = ("$.items[" + 0) + "]";
                            static final String LOOP_A = LOOP_B + ".a";
                            static final String LOOP_B = LOOP_A + ".b";
                            static String mutable = "$.a";

                            void read(String json, int i) {
                                JsonPath.read(json, ITEM);
                                JsonPath.read(json, LOOP_A);
                                JsonPath.read(json, mutable);
                                JsonPath.read(json, "$.items[" + i + "]");
                            }
                        }
                        """, source -> source.after(actual -> actual)));
    }

    @Test
    void assessesWebTestClientExpressions() {
        rewriteRun(java("""
                        import org.springframework.test.web.reactive.server.WebTestClient;

                        class ApiTest {
                            void check(WebTestClient.BodyContentSpec body) {
                                body.jsonPath("$.items.length()");
                            }
                        }
                        """, """
                        import org.springframework.test.web.reactive.server.WebTestClient;

                        class ApiTest {
                            void check(WebTestClient.BodyContentSpec body) {
                                /*~~(NOT_RFC_9535: Path functions like .length() are not part of RFC 9535. Take the size of the result in Java (values().size()), or filter with length(), e.g. $[?length(@.tags) > 2].)~~>*/body.jsonPath("$.items.length()");
                            }
                        }
                        """));
    }

    @Test
    void assessesKotlinSources() {
        rewriteRun(spec -> spec.parser(KotlinParser.builder().classpath("json-path")), kotlin("""
                        import com.jayway.jsonpath.JsonPath

                        fun color(json: String): Any = JsonPath.read(json, "\\$.store.bicycle.color")
                        fun titles(json: String): Any = JsonPath.read(json, "$.store.book[*].title")
                        """, """
                        import com.jayway.jsonpath.JsonPath

                        fun color(json: String): Any = /*~~(VALID_SINGLE_VALUE: Valid RFC 9535. Jayway returns a single value here, RFC 9535 a node list: use NodeList.single().)~~>*/JsonPath.read(json, "\\$.store.bicycle.color")
                        fun titles(json: String): Any = /*~~(VALID: Valid RFC 9535. Results can still differ (slices, type coercion); check with JaywayComparison.)~~>*/JsonPath.read(json, "$.store.book[*].title")
                        """));
    }

    @Test
    void leavesUnrelatedCodeAlone() {
        rewriteRun(java("""
                        class Plain {
                            String read(String a, String b) {
                                return a + b;
                            }
                        }
                        """));
    }
}
