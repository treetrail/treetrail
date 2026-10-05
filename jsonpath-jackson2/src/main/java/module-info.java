/**
 * JSONPath (RFC 9535) adapter for Jackson 2 trees ({@code com.fasterxml.jackson.databind.JsonNode}).
 */
module com.christophsens.jsonpath.jackson2 {
    requires transitive com.christophsens.jsonpath;
    requires transitive com.fasterxml.jackson.databind;

    exports com.christophsens.jsonpath.jackson2;
}
