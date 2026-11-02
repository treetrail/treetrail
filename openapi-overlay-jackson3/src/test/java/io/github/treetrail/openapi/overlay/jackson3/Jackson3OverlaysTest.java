package io.github.treetrail.openapi.overlay.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.openapi.overlay.InvalidOverlayException;
import io.github.treetrail.openapi.overlay.Overlay;
import io.github.treetrail.openapi.overlay.OverlayChange;
import io.github.treetrail.openapi.overlay.OverlayDocuments;
import io.github.treetrail.openapi.overlay.OverlayDocuments.Format;
import io.github.treetrail.openapi.overlay.OverlayException;
import io.github.treetrail.openapi.overlay.OverlayOptions;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class Jackson3OverlaysTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static final String OVERLAY = """
            overlay: 1.1.0
            info: {title: Test, version: 1.0.0}
            x-team: api
            actions:
              - target: $.info
                update: {x-audience: internal}
              - target: $.paths['/missing']
                remove: true
            """;

    @Test
    void appliesAnOverlayToAJackson3Document() {
        Overlay overlay = Overlay.parse(OVERLAY);
        ObjectNode document = NODES.objectNode();
        document.putObject("info").put("title", "Pets");

        Jackson3OverlayResult result = Jackson3Overlays.applyTo(overlay, document);

        assertThat(result.document().get("info").get("x-audience").stringValue())
                .isEqualTo("internal");
        assertThat(document.get("info").has("x-audience"))
                .as("the input is unchanged")
                .isFalse();
        assertThat(result.changes()).extracting(OverlayChange::kind).containsExactly(OverlayChange.Kind.MERGED);
        assertThat(result.warnings())
                .singleElement()
                .hasToString("/actions/1: target $.paths['/missing'] selects no node");
    }

    @Test
    void passesTheOptions() {
        Overlay overlay = Overlay.parse(OVERLAY);
        ObjectNode document = NODES.objectNode();
        document.putObject("info");

        assertThatThrownBy(() ->
                        Jackson3Overlays.applyTo(overlay, document, OverlayOptions.DEFAULT.withFailOnNoMatch(true)))
                .isInstanceOf(OverlayException.class)
                .hasMessageContaining("selects no node");
    }

    @Test
    void readsAndValidatesOverlaysAsJackson3Trees() {
        JsonNode valid = Jackson3OverlayDocuments.parse(OVERLAY);
        Overlay overlay = Jackson3Overlays.overlayOf(valid);
        assertThat(overlay.actions()).hasSize(2);
        assertThat(Jackson3Overlays.validate(valid)).isEmpty();
        assertThat(Jackson3Overlays.extensions(overlay)).containsOnlyKeys("x-team");
        assertThat(Jackson3Overlays.extensions(overlay).get("x-team").stringValue())
                .isEqualTo("api");

        JsonNode invalid = Jackson3OverlayDocuments.parse("overlay: 1.1.0\nactions: []\n");
        assertThat(Jackson3Overlays.validate(invalid)).isNotEmpty();
        assertThatThrownBy(() -> Jackson3Overlays.overlayOf(invalid)).isInstanceOf(InvalidOverlayException.class);
    }

    @Test
    void convertsEveryKindOfValueExactly() {
        ObjectNode document = NODES.objectNode();
        document.put("short", (short) 7);
        document.put("int", 1);
        document.put("long", 1L << 40);
        document.put("big", new BigInteger("123456789012345678901234567890"));
        document.put("float", 1.5f);
        document.put("double", 0.1);
        document.put("decimal", new BigDecimal("1.50"));
        document.put("string", "x");
        document.put("true", true);
        document.putNull("null");
        document.putArray("array").add(1).add("a").addObject().put("z", 1);
        document.putObject("order").put("z", 1).put("a", 2);

        com.fasterxml.jackson.databind.JsonNode jackson2 = Jackson3Overlays.toJackson2(document);

        assertThat(jackson2.get("short").isShort()).isTrue();
        assertThat(jackson2.get("int").isInt()).isTrue();
        assertThat(jackson2.get("long").isLong()).isTrue();
        assertThat(jackson2.get("big").bigIntegerValue()).isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(jackson2.get("float").isFloat()).isTrue();
        assertThat(jackson2.get("double").doubleValue()).isEqualTo(0.1);
        assertThat(jackson2.get("decimal").decimalValue()).isEqualTo(new BigDecimal("1.50"));
        assertThat(jackson2.get("order").fieldNames()).toIterable().containsExactly("z", "a");
        assertThat(Jackson3Overlays.toJackson3(jackson2)).isEqualTo(document);
        assertThat(OverlayDocuments.write(jackson2, Format.JSON)).contains("\"decimal\": 1.50");
    }

    @Test
    void rejectsNodesThatAreNoJsonValues() {
        ObjectNode jackson3 = NODES.objectNode();
        jackson3.set("binary", NODES.binaryNode(new byte[] {1}));
        assertThatThrownBy(() -> Jackson3Overlays.toJackson2(jackson3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a JSON value: BINARY");
        assertThatThrownBy(() -> Jackson3Overlays.toJackson2(NODES.missingNode()))
                .isInstanceOf(IllegalArgumentException.class);

        com.fasterxml.jackson.databind.node.ArrayNode jackson2 =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        jackson2.addPOJO(new Object());
        assertThatThrownBy(() -> Jackson3Overlays.toJackson3(jackson2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a JSON value: POJO");
    }

    @Test
    void readsAndWritesDocumentsAsOverlayDocumentsDoes(@TempDir Path directory) throws Exception {
        String yaml = "countries: [NO, SE]\nprice: 1.50\nvalid: true\n";
        JsonNode document = Jackson3OverlayDocuments.parse(yaml);
        assertThat(document.get("countries").get(0).isString()).isTrue();
        assertThat(document.get("price").decimalValue()).isEqualTo(new BigDecimal("1.50"));
        assertThat(Jackson3OverlayDocuments.write(document, Format.YAML))
                .isEqualTo(OverlayDocuments.write(OverlayDocuments.parse(yaml), Format.YAML));
        assertThat(Jackson3OverlayDocuments.parse("{\"a\": 1}", Format.JSON)
                        .get("a")
                        .intValue())
                .isEqualTo(1);

        Path json = directory.resolve("openapi.json");
        Jackson3OverlayDocuments.write(document, json);
        assertThat(Files.readString(json)).isEqualTo(OverlayDocuments.write(OverlayDocuments.parse(yaml), Format.JSON));
        assertThat(Jackson3OverlayDocuments.read(json)).isEqualTo(document);
    }
}
