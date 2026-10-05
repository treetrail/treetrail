/**
 * JSONPath (RFC 9535) adapter for Gson trees ({@code com.google.gson.JsonElement}).
 */
module io.github.treetrail.jsonpath.gson {
    requires transitive io.github.treetrail.jsonpath;
    requires transitive com.google.gson;

    exports io.github.treetrail.jsonpath.gson;
}
