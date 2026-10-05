package io.github.treetrail.jsonpath.internal;

/**
 * A node of the document together with its location.
 */
public record Located(Object value, Location location) {
}
