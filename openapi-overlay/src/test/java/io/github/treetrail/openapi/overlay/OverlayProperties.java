package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.jackson2.Jackson2Model;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Properties of the merge rules on random documents, with few member names so that they collide often. */
class OverlayProperties {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final List<String> NAMES = List.of("a", "b", "k", "x-y");

    @Provide
    Arbitrary<JsonNode> values() {
        return Arbitraries.recursive(this::primitives, this::containers, 0, 4);
    }

    @Provide
    Arbitrary<ObjectNode> objects() {
        return objectsOf(values());
    }

    private Arbitrary<JsonNode> primitives() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(-3, 3).map(NODES::numberNode),
                Arbitraries.of("x", "y", "").map(NODES::textNode),
                Arbitraries.of(true, false).map(NODES::booleanNode),
                Arbitraries.just(NODES.nullNode()));
    }

    private Arbitrary<JsonNode> containers(Arbitrary<JsonNode> inner) {
        return Arbitraries.oneOf(objectsOf(inner).map(JsonNode.class::cast), arraysOf(inner));
    }

    private static Arbitrary<ObjectNode> objectsOf(Arbitrary<JsonNode> values) {
        return Arbitraries.maps(Arbitraries.of(NAMES), values).ofMaxSize(4).map(members -> {
            ObjectNode object = NODES.objectNode();
            members.forEach(object::set);
            return object;
        });
    }

    private static Arbitrary<JsonNode> arraysOf(Arbitrary<JsonNode> values) {
        return values.list().ofMaxSize(3).map(elements -> {
            ArrayNode array = NODES.arrayNode();
            elements.forEach(array::add);
            return array;
        });
    }

    @Provide
    Arbitrary<String> targets() {
        return Arbitraries.of(
                "$",
                "$.a",
                "$.b",
                "$..a",
                "$..k",
                "$..*",
                "$.*",
                "$..[0]",
                "$..[-1]",
                "$..[?@.a]",
                "$..[?@ == 'x']",
                "$..[?@.k == 1]",
                "$..[0, 0]",
                "$..[1:]",
                "$['x-y']");
    }

    @Provide
    Arbitrary<Overlay> overlays() {
        Arbitrary<String> action = Combinators.combine(
                        targets(), values(), Arbitraries.integers().between(0, 2), targets())
                .as((target, value, kind, copy) -> switch (kind) {
                    case 0 -> "{\"target\": " + quote(target) + ", \"remove\": true}";
                    case 1 -> "{\"target\": " + quote(target) + ", \"update\": " + value + "}";
                    default -> "{\"target\": " + quote(target) + ", \"copy\": " + quote(copy) + "}";
                });
        return action.list()
                .uniqueElements()
                .ofMinSize(1)
                .ofMaxSize(4)
                .map(actions -> Overlay.parse(
                        "{\"overlay\": \"1.1.0\", \"info\": {\"title\": \"t\", \"version\": \"1\"}, \"actions\": "
                                + actions + "}"));
    }

    private static String quote(String text) {
        return NODES.textNode(text).toString();
    }

    @Property
    void theInputIsNeverChanged(@ForAll("overlays") Overlay overlay, @ForAll("values") JsonNode document) {
        JsonNode before = document.deepCopy();
        for (OverlayOptions options : List.of(OverlayOptions.DEFAULT, OverlayOptions.DEFAULT.withLenientMerge(true))) {
            try {
                overlay.applyTo(document, options);
            } catch (OverlayException expected) {
                // incompatible values, a copy source that is not a single node, removing the root
            }
            assertThat(document).isEqualTo(before);
        }
    }

    @Property
    void whereStrictMergingSucceedsLenientMergingGivesTheSameResult(
            @ForAll("overlays") Overlay overlay, @ForAll("values") JsonNode document) {
        OverlayResult strict;
        try {
            strict = overlay.applyTo(document);
        } catch (OverlayException expected) {
            return;
        }
        OverlayResult lenient = overlay.applyTo(document, OverlayOptions.DEFAULT.withLenientMerge(true));
        assertThat(lenient.document()).isEqualTo(strict.document());
        assertThat(lenient.changes()).isEqualTo(strict.changes());
    }

    @Property
    void mergingAnObjectKeepsTheOtherMembersAndAddsTheNewOnes(
            @ForAll("objects") ObjectNode document, @ForAll("objects") ObjectNode update) {
        JsonNode result;
        try {
            result = overlay("{\"target\": \"$\", \"update\": " + update + "}")
                    .applyTo(document)
                    .document();
        } catch (OverlayException expected) {
            return;
        }
        for (Map.Entry<String, JsonNode> member : document.properties()) {
            if (!update.has(member.getKey())) {
                assertThat(result.get(member.getKey())).isEqualTo(member.getValue());
            }
        }
        for (Map.Entry<String, JsonNode> member : update.properties()) {
            JsonNode before = document.get(member.getKey());
            JsonNode after = result.get(member.getKey());
            if (before == null || (before.isValueNode() && member.getValue().isValueNode())) {
                assertThat(after).isEqualTo(member.getValue());
            } else if (before.isArray()) {
                assertThat(after.size())
                        .isEqualTo(before.size() + member.getValue().size());
            } else {
                assertThat(after.isObject()).isTrue();
            }
        }
    }

    @Property
    void mergingTheSameObjectWithoutArraysTwiceChangesNothingMore(
            @ForAll("objects") ObjectNode document, @ForAll("objects") ObjectNode update) {
        if (hasArray(update)) {
            return;
        }
        Overlay once = overlay("{\"target\": \"$\", \"update\": " + update + "}");
        JsonNode first;
        try {
            first = once.applyTo(document).document();
        } catch (OverlayException expected) {
            return;
        }
        assertThat(once.applyTo(first).document()).isEqualTo(first);
    }

    @Property
    void removingTheNodesAFilterSelectsLeavesNoneThatItSelects(@ForAll("values") JsonNode document) {
        JsonNode result = overlay("{\"target\": \"$..[?@.k]\", \"remove\": true}")
                .applyTo(document)
                .document();
        assertThat(JsonPath.compile("$..[?@.k]").query(result, Jackson2Model.INSTANCE))
                .isEmpty();
    }

    @Property
    void copyingANodeIsUpdatingWithItsValue(@ForAll("values") JsonNode source, @ForAll("values") JsonNode target) {
        ObjectNode document = NODES.objectNode().set("s", source);
        document.set("t", target);
        JsonNode copied;
        try {
            copied = overlay("{\"target\": \"$.t\", \"copy\": \"$.s\"}")
                    .applyTo(document)
                    .document();
        } catch (OverlayException e) {
            assertThat(e).hasMessageContaining("cannot merge");
            return;
        }
        assertThat(overlay("{\"target\": \"$.t\", \"update\": " + source + "}")
                        .applyTo(document)
                        .document())
                .isEqualTo(copied);
    }

    private static Overlay overlay(String action) {
        return Overlay.parse("{\"overlay\": \"1.1.0\", \"info\": {\"title\": \"t\", \"version\": \"1\"}, \"actions\": ["
                + action + "]}");
    }

    private static boolean hasArray(JsonNode node) {
        if (node.isArray()) {
            return true;
        }
        for (Iterator<JsonNode> children = node.elements(); children.hasNext(); ) {
            if (hasArray(children.next())) {
                return true;
            }
        }
        return false;
    }
}
