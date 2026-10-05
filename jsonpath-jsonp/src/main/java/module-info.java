/**
 * JSONPath (RFC 9535) adapter for Jakarta JSON Processing trees ({@code jakarta.json.JsonValue}).
 */
module io.github.treetrail.jsonpath.jsonp {
    requires transitive io.github.treetrail.jsonpath;
    requires transitive jakarta.json;

    exports io.github.treetrail.jsonpath.jsonp;
}
