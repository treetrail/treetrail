package io.github.treetrail.jsonpath.internal;

import org.jspecify.annotations.Nullable;

/**
 * A node of the document together with its location.
 */
public record Located(@Nullable Object value, Location location) {
}
