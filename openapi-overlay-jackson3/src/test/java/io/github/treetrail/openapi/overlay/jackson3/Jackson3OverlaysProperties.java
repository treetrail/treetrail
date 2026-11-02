package io.github.treetrail.openapi.overlay.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.treetrail.openapi.overlay.Overlay;
import io.github.treetrail.openapi.overlay.OverlayException;
import io.github.treetrail.openapi.overlay.OverlayOptions;
import io.github.treetrail.openapi.overlay.OverlayResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Applying an overlay to a Jackson 3 tree gives what applying it to the same Jackson 2 tree gives. */
class Jackson3OverlaysProperties {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final List<String> NAMES = List.of("a", "b", "k");

    @Provide
    Arbitrary<JsonNode> values() {
        return Arbitraries.recursive(this::primitives, this::containers, 0, 4);
    }

    private Arbitrary<JsonNode> primitives() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(-3, 3).map(NODES::numberNode),
                Arbitraries.longs().map(NODES::numberNode),
                Arbitraries.bigIntegers()
                        .between(BigInteger.ZERO, BigInteger.TEN.pow(30))
                        .map(NODES::numberNode),
                Arbitraries.doubles().map(NODES::numberNode),
                Arbitraries.floats().map(NODES::numberNode),
                Arbitraries.bigDecimals().ofScale(3).map(d -> NODES.numberNode(new BigDecimal(d.unscaledValue(), 3))),
                Arbitraries.of("x", "y", "").map(NODES::textNode),
                Arbitraries.of(true, false).map(NODES::booleanNode),
                Arbitraries.just(NODES.nullNode()));
    }

    private Arbitrary<JsonNode> containers(Arbitrary<JsonNode> inner) {
        Arbitrary<JsonNode> objects = Arbitraries.maps(Arbitraries.of(NAMES), inner)
                .ofMaxSize(3)
                .map(members -> {
                    ObjectNode object = NODES.objectNode();
                    members.forEach(object::set);
                    return object;
                });
        Arbitrary<JsonNode> arrays = inner.list().ofMaxSize(3).map(elements -> {
            ArrayNode array = NODES.arrayNode();
            elements.forEach(array::add);
            return array;
        });
        return Arbitraries.oneOf(objects, arrays);
    }

    @Provide
    Arbitrary<String> targets() {
        return Arbitraries.of("$", "$.a", "$..a", "$..*", "$.*", "$..[0]", "$..[?@.k]", "$..[?@ == 'x']");
    }

    @Property
    void convertsBothWaysWithoutChange(@ForAll("values") JsonNode value) {
        tools.jackson.databind.JsonNode jackson3 = Jackson3Overlays.toJackson3(value);
        assertThat(Jackson3Overlays.toJackson2(jackson3)).isEqualTo(value);
        assertThat(Jackson3Overlays.toJackson3(Jackson3Overlays.toJackson2(jackson3)))
                .isEqualTo(jackson3);
    }

    @Property
    void appliesAsOnJackson2(
            @ForAll("values") JsonNode document,
            @ForAll("targets") String target,
            @ForAll("values") JsonNode update,
            @ForAll boolean remove,
            @ForAll boolean lenient) {
        ObjectNode action = NODES.objectNode().put("target", target);
        if (remove) {
            action.put("remove", true);
        } else {
            action.set("update", update);
        }
        ObjectNode overlayDocument = NODES.objectNode().put("overlay", "1.1.0");
        overlayDocument.putObject("info").put("title", "t").put("version", "1");
        overlayDocument.putArray("actions").add(action);
        Overlay overlay = Overlay.of(overlayDocument);
        OverlayOptions options = OverlayOptions.DEFAULT.withLenientMerge(lenient);

        OverlayResult expected;
        try {
            expected = overlay.applyTo(document, options);
        } catch (OverlayException e) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> Jackson3Overlays.applyTo(overlay, Jackson3Overlays.toJackson3(document), options))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage(e.getMessage());
            return;
        }
        Jackson3OverlayResult result =
                Jackson3Overlays.applyTo(overlay, Jackson3Overlays.toJackson3(document), options);

        assertThat(Jackson3Overlays.toJackson2(result.document())).isEqualTo(expected.document());
        assertThat(result.changes()).isEqualTo(expected.changes());
        assertThat(result.warnings()).isEqualTo(expected.warnings());
    }
}
