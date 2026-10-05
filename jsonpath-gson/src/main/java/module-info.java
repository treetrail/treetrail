/**
 * JSONPath (RFC 9535) adapter for Gson trees ({@code com.google.gson.JsonElement}).
 */
module com.christophsens.jsonpath.gson {
    requires transitive com.christophsens.jsonpath;
    requires transitive com.google.gson;

    exports com.christophsens.jsonpath.gson;
}
