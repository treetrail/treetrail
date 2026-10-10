/**
 * Tests for {@code JsonModel} implementations: the JSONPath Compliance Test Suite and the model contract.
 */
module io.github.treetrail.jsonpath.testkit {
    requires static transitive org.jspecify;
    requires transitive io.github.treetrail.jsonpath;
    requires transitive org.junit.jupiter.api;

    exports io.github.treetrail.jsonpath.testkit;
}
