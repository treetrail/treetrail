package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import io.github.treetrail.jsonpath.jackson2.Jackson2Model;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every overlay and statement on docs/openapi-overlays.md, checked against the page's example description. */
class DocsTest {

    private static final String PAGE = page();
    private static final JsonNode DESCRIPTION =
            OverlayDocuments.parse(blocks("openapi:").get(0));

    private static String page() {
        try {
            return Files.readString(Path.of("../docs/openapi-overlays.md"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The ```yaml blocks of the page that start with the given text. */
    private static List<String> blocks(String start) {
        List<String> blocks = new ArrayList<>();
        int from = 0;
        while ((from = PAGE.indexOf("```yaml\n", from)) >= 0) {
            int begin = from + "```yaml\n".length();
            int end = PAGE.indexOf("```", begin);
            String block = PAGE.substring(begin, end);
            if (block.startsWith(start)) {
                blocks.add(block);
            }
            from = end + 3;
        }
        return blocks;
    }

    private static OverlayResult apply(String title) {
        for (String block : blocks("overlay:")) {
            if (block.contains("title: " + title + ",")) {
                return Overlay.parse(block).applyTo(DESCRIPTION, OverlayOptions.DEFAULT.withFailOnNoMatch(true));
            }
        }
        throw new AssertionError("No overlay titled " + title);
    }

    private static List<String> select(String query, JsonNode document) {
        return JsonPath.compile(query).query(document, Jackson2Model.INSTANCE).paths();
    }

    @Test
    void everyOverlayOnThePageAppliesWithoutWarnings() {
        List<String> overlays = blocks("overlay:");
        assertThat(overlays).hasSize(7);
        for (String overlay : overlays) {
            OverlayResult result =
                    Overlay.parse(overlay).applyTo(DESCRIPTION, OverlayOptions.DEFAULT.withFailOnNoMatch(true));
            assertThat(result.warnings()).as(overlay).isEmpty();
            assertThat(result.changes()).as(overlay).isNotEmpty();
        }
    }

    @Test
    void recipesDoWhatThePageSays() {
        assertThat(select("$..operationId", apply("Remove internal operations").document()))
                .hasSize(2);
        JsonNode headers = apply("Add a request id header").document();
        assertThat(select("$.paths.*.*.parameters[?@.name == 'X-Request-Id']", headers))
                .hasSize(3);
        assertThat(apply("Add a request id header").changes())
                .first()
                .hasToString("/actions/0: MERGED $['paths']['/pets']['get']");
        JsonNode codegen = apply("Prepare for a code generator").document();
        assertThat(select("$.paths.*[?@.operationId == 'findAllPets']", codegen))
                .hasSize(1);
        assertThat(select("$..parameters[?!@.schema]", codegen)).isEmpty();
        JsonNode local = apply("Local servers").document();
        assertThat(local.get("servers")).hasSize(1);
        assertThat(local.at("/servers/0/url").textValue()).isEqualTo("http://localhost:8080");
        assertThat(select(
                        "$.paths.*.*.responses['404']", apply("Error responses").document()))
                .hasSize(2);
        JsonNode renamed = apply("Rename a path").document();
        assertThat(renamed.get("paths").has("/pets")).isFalse();
        assertThat(renamed.get("paths").get("/animals"))
                .isEqualTo(DESCRIPTION.get("paths").get("/pets"));
        JsonNode descriptions = apply("Replace descriptions").document();
        assertThat(descriptions.at("/info/description").textValue()).isEqualTo("See the developer portal.");
        assertThat(descriptions
                        .at("/components/schemas/Pet/properties/description/type")
                        .textValue())
                .isEqualTo("string");
        assertThat(descriptions
                        .at("/components/schemas/Pet/properties/description/description")
                        .textValue())
                .isEqualTo("See the developer portal.");
    }

    @Test
    void targetsSelectWhatThePageSays() {
        assertThat(select("$.paths.*[?@.operationId == 'showPet']", DESCRIPTION))
                .containsExactly("$['paths']['/pets/{id}']['get']");
        assertThat(select("$.paths.*.*[?@.operationId == 'showPet']", DESCRIPTION))
                .isEmpty();
        assertThat(select("$.paths[?@.get && !@.post]", DESCRIPTION)).containsExactly("$['paths']['/pets/{id}']");
        assertThat(select("$.paths.*[?(@.operationId == 'showPet')]", DESCRIPTION))
                .isEqualTo(select("$.paths.*[?@.operationId == 'showPet']", DESCRIPTION));
        assertThatThrownBy(() -> JsonPath.compile("$.paths.*[?@.x-internal]"))
                .isInstanceOf(JsonPathSyntaxException.class);
        assertThat(select("$.paths.*[?search(@.summary, '[Dd]eprecated')]", DESCRIPTION))
                .containsExactly("$['paths']['/pets']['post']");
        assertThat(select("$..description", DESCRIPTION))
                .hasSizeGreaterThan(select("$..[?search(@.description, '')].description", DESCRIPTION)
                        .size());
        assertThat(select("$.servers[-1:]", DESCRIPTION)).containsExactly("$['servers'][0]");
    }

    @Test
    void theJaywayTableIsRight() {
        String json = OverlayDocuments.write(OverlayDocuments.parse("""
                {paths: {/pets: {get: {operationId: x, limit: 100}}}, book: [1, 2]}
                """), OverlayDocuments.Format.JSON);
        JsonNode document = OverlayDocuments.parse(json);

        // Jayway filters the path items themselves: nothing; RFC 9535 the operations.
        assertThat(jayway("$.paths.*[?(@.operationId == 'x')]", json)).isEmpty();
        assertThat(select("$.paths.*[?(@.operationId == 'x')]", document)).hasSize(1);
        // '100' == 100
        assertThat(jayway("$.paths.*.*[?(@.limit == '100')]", json)).hasSize(1);
        assertThat(select("$.paths.*[?(@.limit == '100')]", document)).isEmpty();
        // Jayway-only syntax
        assertThat(jayway("$.paths.*.*[?(@.operationId =~ /x.*/)]", json)).hasSize(1);
        assertThatThrownBy(() -> JsonPath.compile("$.paths.*[?(@.operationId =~ /x.*/)]"))
                .isInstanceOf(JsonPathSyntaxException.class);
        assertThat(com.jayway.jsonpath.JsonPath.read(json, "$..book.length()").toString())
                .isEqualTo("2");
        assertThatThrownBy(() -> JsonPath.compile("$..book.length()")).isInstanceOf(JsonPathSyntaxException.class);
    }

    private static List<String> jayway(String query, String json) {
        try {
            return com.jayway.jsonpath.JsonPath.using(
                            Configuration.defaultConfiguration().addOptions(Option.AS_PATH_LIST))
                    .parse(json)
                    .read(query);
        } catch (PathNotFoundException e) {
            return List.of();
        } catch (InvalidPathException e) {
            throw new AssertionError(e);
        }
    }
}
