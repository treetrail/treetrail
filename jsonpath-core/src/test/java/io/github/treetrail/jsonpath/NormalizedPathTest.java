package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.NormalizedPath.Index;
import io.github.treetrail.jsonpath.NormalizedPath.Name;
import io.github.treetrail.jsonpath.testkit.ComplianceCase;
import io.github.treetrail.jsonpath.testkit.JsonModelTestKit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NormalizedPathTest {

    @Test
    void roundTripsTheLocationOfEveryNodeSelectedInTheComplianceSuite() {
        int nodes = 0;
        for (ComplianceCase test : JsonModelTestKit.complianceCases()) {
            if (test.invalidSelector()) {
                continue;
            }
            for (Node<?> node : JsonPath.compile(test.selector()).query(test.document())) {
                NormalizedPath path = NormalizedPath.parse(node.path());
                assertThat(path).as(node.path()).isEqualTo(node.normalizedPath());
                assertThat(path.toString()).isEqualTo(node.path());
                nodes++;
            }
        }
        assertThat(nodes).isGreaterThan(600);
    }

    @Test
    void parsesEveryResultPathOfTheComplianceSuiteBackToTheSameText() {
        List<String> paths = new ArrayList<>();
        for (ComplianceCase test : JsonModelTestKit.complianceCases()) {
            test.resultPaths().forEach(paths::addAll);
        }
        assertThat(paths).hasSizeGreaterThan(500);
        for (String path : paths) {
            assertThat(NormalizedPath.parse(path).toString()).isEqualTo(path);
        }
    }

    @Test
    void hasTypedSteps() {
        NormalizedPath path = NormalizedPath.parse("$['store']['book'][0]");

        assertThat(path.steps()).containsExactly(new Name("store"), new Name("book"), new Index(0));
        assertThat(path)
                .isEqualTo(NormalizedPath.root().child("store").child("book").child(0))
                .isEqualTo(NormalizedPath.of(List.of(new Name("store"), new Name("book"), new Index(0))))
                .hasSameHashCodeAs(
                        NormalizedPath.root().child("store").child("book").child(0))
                .isNotEqualTo(NormalizedPath.parse("$['store']['book']['0']"));
        assertThat(NormalizedPath.parse("$")).isSameAs(NormalizedPath.root());
        assertThat(NormalizedPath.root().steps()).isEmpty();
    }

    @Test
    void convertsToAJsonPointer() {
        assertThat(NormalizedPath.root().toJsonPointer()).isEmpty();
        assertThat(NormalizedPath.parse("$['store']['book'][0]").toJsonPointer())
                .isEqualTo("/store/book/0");
        assertThat(NormalizedPath.parse("$['a/b']['m~n']['~1']").toJsonPointer())
                .isEqualTo("/a~1b/m~0n/~01");
        assertThat(NormalizedPath.parse("$['']").toJsonPointer()).isEqualTo("/");
        assertThat(NormalizedPath.parse("$['😀']").toJsonPointer()).isEqualTo("/😀");
    }

    @Test
    void escapesMemberNamesAsTheRfcPrescribes() {
        String name = "a'b\\c\b\f\n\r\t\u0000\u0007\u000b\u000e\u001f😀";
        NormalizedPath path = NormalizedPath.root().child(name);

        assertThat(path.toString()).isEqualTo("$['a\\'b\\\\c\\b\\f\\n\\r\\t\\u0000\\u0007\\u000b\\u000e\\u001f😀']");
        assertThat(NormalizedPath.parse(path.toString())).isEqualTo(path);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "@",
                "$.a",
                "$[\"a\"]",
                "$['a'",
                "$['a']x",
                "$[a]",
                "$[01]",
                "$[-1]",
                "$[2147483648]",
                "$['\\x']",
                "$['\\u000a']",
                "$['\\u001F']",
                "$['\\u0020']",
                "$['\\u12']",
                "$['\\'",
                "$['\n']",
                "$['\uD800']",
                "$[ 'a']"
            })
    void rejectsTextThatIsNotANormalizedPath(String text) {
        assertThatThrownBy(() -> NormalizedPath.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Not a normalized path: ");
    }

    @Test
    void rejectsNegativeIndices() {
        assertThatThrownBy(() -> NormalizedPath.root().child(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesQueriesByExpressionAndLimits() {
        JsonPath path = JsonPath.compile("$.a");

        assertThat(path.expression()).isEqualTo("$.a");
        assertThat(path).isEqualTo(JsonPath.compile("$.a")).hasSameHashCodeAs(JsonPath.compile("$.a"));
        assertThat(path).isNotEqualTo(JsonPath.compile("$['a']"));
        assertThat(path).isNotEqualTo(path.withLimits(EvaluationLimits.DEFAULT.withMaxDepth(10)));
    }

    @Test
    void hashesNodesByPath() {
        Node<?> first = JsonPath.compile("$.a").queryJson("{\"a\": [1, 2]}").get(0);
        Node<?> again = JsonPath.compile("$.a").queryJson("{\"a\": [1, 2]}").get(0);

        assertThat(first).isEqualTo(again).hasSameHashCodeAs(again);
        assertThat(first.hashCode()).isEqualTo(NormalizedPath.parse("$['a']").hashCode());
    }

    @Test
    void buildsThePathsOfDeepResultsFromTheirAncestors() {
        // Names that need escaping, indices, and more depth than Located.REUSE_DEPTH.
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            json.append(i % 3 == 0 ? "[" : "{\"k'\\n" + i + "\": ");
        }
        json.append("1");
        for (int i = 39; i >= 0; i--) {
            json.append(i % 3 == 0 ? "]" : "}");
        }
        for (String query : List.of("$..*", "$..[0]", "$..*..*", "$[0]..*", "$..[?@]")) {
            NodeList<Object> nodes = JsonPath.compile(query).queryJson(json.toString());
            List<String> fromRoot = new ArrayList<>();
            for (Node<Object> node : nodes) {
                fromRoot.add(node.normalizedPath().toString());
            }

            assertThat(nodes).as(query).isNotEmpty();
            assertThat(nodes.paths()).as(query).isEqualTo(fromRoot);
            for (Node<Object> node : nodes) {
                assertThat(NormalizedPath.parse(node.path())).isEqualTo(node.normalizedPath());
            }
        }
    }

    @Test
    void navigatesToParentAndLastStep() {
        NormalizedPath path = NormalizedPath.parse("$['store']['book'][0]");

        assertThat(path.isRoot()).isFalse();
        assertThat(path.parent()).contains(NormalizedPath.parse("$['store']['book']"));
        assertThat(path.lastStep()).contains(new Index(0));
        assertThat(NormalizedPath.parse("$['a']").parent()).containsSame(NormalizedPath.root());
        assertThat(NormalizedPath.parse("$['a']").lastStep()).contains(new Name("a"));
        assertThat(NormalizedPath.root().isRoot()).isTrue();
        assertThat(NormalizedPath.root().parent()).isEmpty();
        assertThat(NormalizedPath.root().lastStep()).isEmpty();
    }

    @Test
    void startsWithItselfAndItsAncestors() {
        NormalizedPath path = NormalizedPath.parse("$['a'][1]['b']");

        assertThat(path.startsWith(path)).isTrue();
        assertThat(path.startsWith(NormalizedPath.parse("$['a'][1]"))).isTrue();
        assertThat(path.startsWith(NormalizedPath.root())).isTrue();
        assertThat(path.startsWith(NormalizedPath.parse("$['a'][0]"))).isFalse();
        assertThat(path.startsWith(NormalizedPath.parse("$['a'][1]['b']['c']"))).isFalse();
        assertThat(NormalizedPath.parse("$['ab']").startsWith(NormalizedPath.parse("$['a']")))
                .isFalse();
        assertThat(NormalizedPath.root().startsWith(path)).isFalse();
    }

    @Test
    void sortsInDocumentOrder() {
        List<NormalizedPath> paths = new ArrayList<>(List.of(
                NormalizedPath.parse("$['b']"),
                NormalizedPath.parse("$['a'][10]"),
                NormalizedPath.parse("$['a'][2]['x']"),
                NormalizedPath.parse("$"),
                NormalizedPath.parse("$['a'][2]"),
                NormalizedPath.parse("$['a']"),
                NormalizedPath.parse("$['\uffff']"),
                NormalizedPath.parse("$['😀']")));
        Collections.shuffle(paths, new Random(9535));
        paths.sort(null);

        // Indices numerically, ancestors first; names by Unicode scalar value, so U+1F600 after U+FFFF although
        // its UTF-16 code units sort before.
        assertThat(paths)
                .extracting(NormalizedPath::toString)
                .containsExactly(
                        "$", "$['a']", "$['a'][2]", "$['a'][2]['x']", "$['a'][10]", "$['b']", "$['\uffff']", "$['😀']");
        assertThat(NormalizedPath.parse("$[0]").compareTo(NormalizedPath.parse("$['0']")))
                .isNegative();
        assertThat(NormalizedPath.parse("$['0']").compareTo(NormalizedPath.parse("$[0]")))
                .isPositive();
        assertThat(NormalizedPath.parse("$['a'][1]").compareTo(NormalizedPath.parse("$['a'][1]")))
                .isZero();
    }

    @Test
    void removingInReverseDocumentOrderKeepsTheOtherPathsValid() {
        Object document = JavaObjectModel.parse(
                "{\"a\": [0, {\"drop\": true, \"b\": [{\"drop\": true}, 1]}, 2, {\"drop\": true}]}");
        List<NormalizedPath> paths =
                new ArrayList<>(JsonPath.compile("$..[?@.drop]").query(document).distinctPaths());
        paths.sort(Collections.reverseOrder());
        for (NormalizedPath path : paths) {
            Object parent = path.parent()
                    .orElseThrow()
                    .resolve(document, JavaObjectModel.INSTANCE)
                    .orElseThrow()
                    .value();
            ((List<?>) Objects.requireNonNull(parent))
                    .remove(((Index) path.lastStep().orElseThrow()).index());
        }

        assertThat(JavaObjectModel.jsonEquals(document, JavaObjectModel.parse("{\"a\": [0, 2]}")))
                .isTrue();
    }

    @Test
    void resolvesNodesInJavaObjects() {
        Object document = JavaObjectModel.parse("{\"a\": [1, {\"b\": null}], \"s\": \"x\"}");

        assertThat(NormalizedPath.parse("$['a'][0]").resolve(document, JavaObjectModel.INSTANCE))
                .hasValueSatisfying(node -> {
                    assertThat(JavaObjectModel.jsonEquals(node.value(), 1)).isTrue();
                    assertThat(node.path()).isEqualTo("$['a'][0]");
                });
        // JSON null is a node, too.
        assertThat(NormalizedPath.parse("$['a'][1]['b']").resolve(document, JavaObjectModel.INSTANCE))
                .hasValueSatisfying(node -> assertThat(node.value()).isNull());
        assertThat(NormalizedPath.parse("$['a'][2]").resolve(document, JavaObjectModel.INSTANCE))
                .isEmpty();
        assertThat(NormalizedPath.parse("$['s'][0]").resolve(document, JavaObjectModel.INSTANCE))
                .isEmpty();
        assertThat(NormalizedPath.root().resolve(null, JavaObjectModel.INSTANCE))
                .hasValueSatisfying(node -> assertThat(node.value()).isNull());
        assertThatThrownBy(() -> NormalizedPath.root().resolve(document, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void distinctPathsKeepTheFirstOccurrence() {
        Object document = JavaObjectModel.parse("[10, 11, 12]");

        assertThat(JsonPath.compile("$[2, 0, 2, -1, 0]").query(document).distinctPaths())
                .extracting(NormalizedPath::toString)
                .containsExactly("$[2]", "$[0]");
        assertThat(JsonPath.compile("$[5]").query(document).distinctPaths()).isEmpty();
    }
}
