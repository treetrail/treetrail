/**
 * AssertJ assertions for JSON with JSONPath (RFC 9535).
 */
module io.github.treetrail.jsonpath.assertj {
    requires static transitive org.jspecify;
    requires transitive io.github.treetrail.jsonpath;
    requires transitive org.assertj.core;

    exports io.github.treetrail.jsonpath.assertj;
}
