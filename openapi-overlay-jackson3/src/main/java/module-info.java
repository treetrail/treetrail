/**
 * OpenAPI Overlay for Jackson 3 trees ({@code tools.jackson.databind.JsonNode}), on top of
 * {@code io.github.treetrail.openapi.overlay}.
 */
module io.github.treetrail.openapi.overlay.jackson3 {
    requires static transitive org.jspecify;
    requires transitive io.github.treetrail.openapi.overlay;
    requires transitive tools.jackson.databind;

    exports io.github.treetrail.openapi.overlay.jackson3;
}
