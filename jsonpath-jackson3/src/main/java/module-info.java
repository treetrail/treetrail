/**
 * JSONPath (RFC 9535) adapter for Jackson 3 trees ({@code tools.jackson.databind.JsonNode}).
 */
module io.github.treetrail.jsonpath.jackson3 {
    requires static transitive org.jspecify;
    requires transitive io.github.treetrail.jsonpath;
    requires transitive tools.jackson.databind;

    exports io.github.treetrail.jsonpath.jackson3;
}
