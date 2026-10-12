package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.jackson2.Jackson2Model;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Overlay targets that use RFC 9535, on an OpenAPI description: what they select, that an overlay with them
 * changes exactly those nodes, and what Jayway JsonPath 3.0.0 does with the same expression.
 */
class Rfc9535TargetsTest {

    static final String OPENAPI = """
            openapi: 3.1.0
            info: {title: Pets, version: 1.0.0}
            components:
              schemas:
                Pet: {properties: {description: {type: string}}}
            servers:
              - {url: 'https://dev.example.com', description: Dev}
              - {url: 'https://staging.example.com', description: Staging}
              - {url: 'https://api.example.com', description: Production}
            paths:
              /pets:
                get:
                  operationId: listPets
                  summary: List the pets
                  tags: [pets]
                  x-rate-limit: 100
                  x-oai-traits: [paged]
                  parameters:
                    - {name: limit, in: query}
                    - {name: filter, in: query}
                  responses: {'200': {description: OK}}
                post:
                  operationId: createPet
                  summary: Create a pet (deprecated, use PUT)
                  tags: [pets, admin]
                  responses: {'201': {description: Created}, '400': {description: Bad}}
              /pets/{id}:
                get:
                  operationId: showPet
                  summary: Show a pet
                  tags: [pets]
                  x-internal: true
                  parameters:
                    - {name: id, in: path}
                  responses: {'200': {description: OK}}
              /health:
                get:
                  operationId: health
                  tags: [ops]
                  x-rate-limit: '100'
                  responses: {'200': {description: OK}}
            """;

    /** What Jayway JsonPath does with the target. */
    enum Jayway {
        /** Rejects the expression: an overlay tool built on Jayway fails. */
        REJECTS,
        /**
         * Selects other nodes, or none: an overlay tool built on Jayway changes the wrong nodes or nothing, without
         * an error.
         */
        SELECTS_OTHER_NODES,
        /** Selects the same nodes. */
        SAME
    }

    static Stream<Arguments> targets() {
        return Stream.of(
                // Filters without parentheses: the RFC 9535 syntax.
                Arguments.of(
                        "$.paths.*[?@.operationId == 'showPet']",
                        List.of("$['paths']['/pets/{id}']['get']"),
                        Jayway.REJECTS),
                // The same filter with parentheses, which both accept, but Jayway applies a filter on an object to
                // the object itself, RFC 9535 to its members: Jayway selects nothing.
                Arguments.of(
                        "$.paths.*[?(@.operationId == 'showPet')]",
                        List.of("$['paths']['/pets/{id}']['get']"),
                        Jayway.SELECTS_OTHER_NODES),
                // Logical operators on existence tests.
                Arguments.of(
                        "$.paths[?@.get && !@.post]",
                        List.of("$['paths']['/pets/{id}']", "$['paths']['/health']"),
                        Jayway.REJECTS),
                // Function extensions of RFC 9535: match(), search(), length(), count(), value().
                Arguments.of(
                        "$.paths.*[?match(@.operationId, 'list.*|show.*')]",
                        List.of("$['paths']['/pets']['get']", "$['paths']['/pets/{id}']['get']"),
                        Jayway.REJECTS),
                Arguments.of(
                        "$.paths.*[?search(@.summary, '[Dd]eprecated')]",
                        List.of("$['paths']['/pets']['post']"),
                        Jayway.REJECTS),
                Arguments.of(
                        "$.paths.*.*.parameters[?length(@.name) > 5]",
                        List.of("$['paths']['/pets']['get']['parameters'][1]"),
                        Jayway.REJECTS),
                Arguments.of(
                        "$.paths.*[?count(@.responses.*) > 1]", List.of("$['paths']['/pets']['post']"), Jayway.REJECTS),
                Arguments.of(
                        "$.paths.*[?value(@.tags[0]) == 'ops']",
                        List.of("$['paths']['/health']['get']"),
                        Jayway.REJECTS),
                // A name with '-' needs brackets; Jayway also rejects the shorthand form.
                Arguments.of(
                        "$.paths.*[?@['x-internal'] == true]",
                        List.of("$['paths']['/pets/{id}']['get']"),
                        Jayway.REJECTS),
                // Comparisons are type-strict: the string '100' is not the number 100.
                Arguments.of(
                        "$.paths.*[?@['x-rate-limit'] == 100]", List.of("$['paths']['/pets']['get']"), Jayway.REJECTS),
                // In Jayway's syntax, the operations themselves are filtered, and '100' == 100: Jayway selects both
                // operations with x-rate-limit. In RFC 9535, the members of the operations are filtered: none.
                Arguments.of("$.paths.*.*[?(@['x-rate-limit'] == 100)]", List.of(), Jayway.SELECTS_OTHER_NODES),
                // Slices with a step.
                Arguments.of("$.servers[::2]", List.of("$['servers'][0]", "$['servers'][2]"), Jayway.REJECTS),
                Arguments.of("$.servers[-1:]", List.of("$['servers'][2]"), Jayway.SAME),
                // Nested filters, as in the traits example of the specification.
                Arguments.of("$.paths[?@.*.tags[?@ == 'admin']]", List.of("$['paths']['/pets']"), Jayway.REJECTS),
                // The target of the traits example of the specification: Jayway cannot parse the nested filter.
                Arguments.of(
                        "$.paths[?(@.get['x-oai-traits'][?(@ == 'paged')])].get",
                        List.of("$['paths']['/pets']['get']"),
                        Jayway.REJECTS),
                // Jayway filters the paths object itself instead of its members: it selects nothing.
                Arguments.of(
                        "$.paths[?(@.get['x-internal'] == true)].get",
                        List.of("$['paths']['/pets/{id}']['get']"),
                        Jayway.SELECTS_OTHER_NODES),
                // Only string descriptions, not schema properties named description: search() is false for
                // anything but a string, and an empty pattern matches every string.
                Arguments.of(
                        "$..[?search(@.description, '')].description",
                        List.of(
                                "$['servers'][0]['description']",
                                "$['servers'][1]['description']",
                                "$['servers'][2]['description']",
                                "$['paths']['/pets']['get']['responses']['200']['description']",
                                "$['paths']['/pets']['post']['responses']['201']['description']",
                                "$['paths']['/pets']['post']['responses']['400']['description']",
                                "$['paths']['/pets/{id}']['get']['responses']['200']['description']",
                                "$['paths']['/health']['get']['responses']['200']['description']"),
                        Jayway.REJECTS),
                // Paths without filters work the same.
                Arguments.of(
                        "$.paths['/pets'].*.responses['200']",
                        List.of("$['paths']['/pets']['get']['responses']['200']"),
                        Jayway.SAME),
                Arguments.of(
                        "$..parameters[?(@.in == 'path')]",
                        List.of("$['paths']['/pets/{id}']['get']['parameters'][0]"),
                        Jayway.SAME));
    }

    @ParameterizedTest
    @MethodSource("targets")
    void treetrailSelects(String target, List<String> expected, Jayway jayway) {
        JsonNode document = OverlayApplyTest.yaml(OPENAPI);
        assertThat(JsonPath.compile(target)
                        .query(document, Jackson2Model.INSTANCE)
                        .paths())
                .containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @MethodSource("targets")
    void anOverlayChangesExactlyTheSelectedNodes(String target, List<String> expected, Jayway jayway) {
        JsonNode document = OverlayApplyTest.yaml(OPENAPI);
        // Objects get a member x-hit; primitives are replaced by the string x-hit.
        boolean primitives = !expected.isEmpty()
                && JsonPath.compile(target)
                        .query(document, Jackson2Model.INSTANCE)
                        .get(0)
                        .value()
                        .isValueNode();
        JsonNode result = Overlay.parse("overlay: 1.1.0\ninfo: {title: t, version: '1'}\nactions:\n" + "  - target: \""
                        + target.replace("\"", "\\\"") + "\"\n    update: " + (primitives ? "x-hit" : "{x-hit: true}")
                        + "\n")
                .applyTo(document)
                .document();
        String hits = primitives ? "$..[?@ == 'x-hit']" : "$..[?@['x-hit']]";
        assertThat(JsonPath.compile(hits).query(result, Jackson2Model.INSTANCE).paths())
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @MethodSource("targets")
    void jaywayJsonPath(String target, List<String> expected, Jayway jayway) {
        String json = OverlayDocuments.write(OverlayApplyTest.yaml(OPENAPI), OverlayDocuments.Format.JSON);
        Jayway actual;
        List<String> selected = List.of();
        try {
            com.jayway.jsonpath.JsonPath path = com.jayway.jsonpath.JsonPath.compile(target);
            selected = com.jayway.jsonpath.JsonPath.using(
                            Configuration.defaultConfiguration().addOptions(Option.AS_PATH_LIST))
                    .parse(json)
                    .read(path);
            actual = selected.equals(expected) ? Jayway.SAME : Jayway.SELECTS_OTHER_NODES;
        } catch (PathNotFoundException e) {
            // Jayway's way to say that a path list is empty.
            actual = expected.isEmpty() ? Jayway.SAME : Jayway.SELECTS_OTHER_NODES;
        } catch (InvalidPathException e) {
            // Also thrown while reading, for example for an unknown function.
            actual = Jayway.REJECTS;
            selected = List.of(e.getMessage());
        }
        assertThat(actual).as("Jayway selects %s", selected).isEqualTo(jayway);
    }
}
