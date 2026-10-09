/**
 * JSONPath (RFC 9535) adapter for Jackson 2 trees ({@code com.fasterxml.jackson.databind.JsonNode}).
 */
module io.github.treetrail.jsonpath.jackson2 {
    requires static transitive org.jspecify;
    requires transitive io.github.treetrail.jsonpath;
    requires transitive com.fasterxml.jackson.databind;

    exports io.github.treetrail.jsonpath.jackson2;
}
