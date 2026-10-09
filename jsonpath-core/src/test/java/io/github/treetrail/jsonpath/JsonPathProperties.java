package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Properties that must hold for every document, not just for the examples of the compliance suite.
 */
class JsonPathProperties {

    @Provide
    Arbitrary<Object> documents() {
        return JsonArbitraries.documents();
    }

    @Provide
    Arbitrary<Object> values() {
        return JsonArbitraries.values();
    }

    /** Normalized paths (RFC 9535, section 2.7) are queries that select exactly the node they name. */
    @Property(tries = 500)
    void normalizedPathSelectsExactlyItsNode(@ForAll("documents") Object document) {
        NodeList<Object> all = JsonPath.compile("$..*").query(document);
        for (Node<Object> node : all) {
            NodeList<Object> selected = JsonPath.compile(node.path()).query(document);

            assertThat(selected).hasSize(1);
            assertThat(selected.get(0).path()).isEqualTo(node.path());
            assertThat(selected.get(0).location()).isEqualTo(node.location());
            assertThat(selected.get(0).value()).isSameAs(node.value());
            assertThat(JsonPath.compile(node.path()).isSingular()).isTrue();
        }
        assertThat(JsonPath.compile("$").query(document).paths()).containsExactly("$");
    }

    /** `$..*` visits every node once: each normalized path occurs exactly once. */
    @Property(tries = 500)
    void descendantsAreDistinct(@ForAll("documents") Object document) {
        List<String> paths = JsonPath.compile("$..*").query(document).paths();

        assertThat(paths).doesNotHaveDuplicates();
    }

    /** Compiling is deterministic: two compilations of an expression select the same nodes. */
    @Property(tries = 300)
    void compilationIsDeterministic(@ForAll("documents") Object document) {
        for (String expression : List.of(
                "$..*",
                "$..[?@.a]",
                "$[?@ == 1]",
                "$..[?length(@) > 1]",
                "$..[0, -1, 1:3, ::-1]",
                "$..[?match(@, '[a-c]+') || search(@, 'é|😀')]",
                "$..[?@.a == @.b && !@.c]")) {
            JsonPath first = JsonPath.compile(expression);
            JsonPath second = JsonPath.compile(expression);

            assertThat(second.toString()).isEqualTo(first.toString());
            assertThat(second.query(document).paths())
                    .isEqualTo(first.query(document).paths());
        }
    }

    /** Comparisons are consistent (RFC 9535, section 2.3.5.2.2) whatever the types of the operands. */
    @Property(tries = 2_000)
    void comparisonsAreConsistent(@ForAll("values") Object left, @ForAll("values") Object right) {
        Object document = JsonArbitraries.pair(left, right);
        boolean eq = selects("$[?@.l == @.r]", document);
        boolean ne = selects("$[?@.l != @.r]", document);
        boolean lt = selects("$[?@.l < @.r]", document);
        boolean le = selects("$[?@.l <= @.r]", document);
        boolean gt = selects("$[?@.l > @.r]", document);
        boolean ge = selects("$[?@.l >= @.r]", document);

        assertThat(eq).as("== is symmetric").isEqualTo(selects("$[?@.r == @.l]", document));
        assertThat(eq).as("== agrees with jsonEquals").isEqualTo(JavaObjectModel.jsonEquals(left, right));
        assertThat(JavaObjectModel.jsonEquals(right, left))
                .as("jsonEquals is symmetric")
                .isEqualTo(eq);
        assertThat(ne).as("!= is the negation of ==").isEqualTo(!eq);
        assertThat(lt && gt).as("< and > exclude each other").isFalse();
        assertThat(lt).as("< mirrors >").isEqualTo(selects("$[?@.r > @.l]", document));
        assertThat(le).as("<= is < or ==").isEqualTo(lt || eq);
        assertThat(ge).as(">= is > or ==").isEqualTo(gt || eq);
    }

    /** Every value equals itself, also through a query. */
    @Property(tries = 500)
    void equalityIsReflexive(@ForAll("values") Object value) {
        assertThat(JavaObjectModel.jsonEquals(value, value)).isTrue();
        assertThat(selects("$[?@.l == @.r]", JsonArbitraries.pair(value, value)))
                .isTrue();
    }

    private static boolean selects(String expression, Object document) {
        return !JsonPath.compile(expression).query(document).isEmpty();
    }
}
