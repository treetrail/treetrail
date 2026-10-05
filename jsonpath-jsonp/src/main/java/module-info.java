/**
 * JSONPath (RFC 9535) adapter for Jakarta JSON Processing trees ({@code jakarta.json.JsonValue}).
 */
module com.christophsens.jsonpath.jsonp {
    requires transitive com.christophsens.jsonpath;
    requires transitive jakarta.json;

    exports com.christophsens.jsonpath.jsonp;
}
