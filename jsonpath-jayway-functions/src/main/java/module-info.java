/**
 * Jayway JsonPath's aggregate functions as function extensions; not part of RFC 9535.
 */
module io.github.treetrail.jsonpath.jayway {
    requires static transitive org.jspecify;
    requires transitive io.github.treetrail.jsonpath;

    exports io.github.treetrail.jsonpath.jayway;
}
