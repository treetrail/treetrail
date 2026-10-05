/**
 * JSONPath (RFC 9535) adapter for Jackson 3 trees ({@code tools.jackson.databind.JsonNode}).
 */
module com.christophsens.jsonpath.jackson3 {
    requires transitive com.christophsens.jsonpath;
    requires transitive tools.jackson.databind;

    exports com.christophsens.jsonpath.jackson3;
}
