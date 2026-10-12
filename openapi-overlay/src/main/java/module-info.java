/**
 * OpenAPI Overlay (versions 1.0, 1.1 and 1.2): parses overlays and applies them to OpenAPI descriptions, or any
 * other JSON document, as Jackson 2 trees (Jackson 3 on request: https://github.com/treetrail/treetrail/issues/98). Targets are RFC 9535 JSONPath queries,
 * evaluated by Treetrail.
 */
module io.github.treetrail.openapi.overlay {
    requires static transitive org.jspecify;
    requires transitive io.github.treetrail.jsonpath.jackson2;
    requires transitive com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires org.yaml.snakeyaml;

    exports io.github.treetrail.openapi.overlay;
}
