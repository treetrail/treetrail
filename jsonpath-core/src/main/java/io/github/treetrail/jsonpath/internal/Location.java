package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.NormalizedPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Location of a node in a document, kept as a parent-linked list so that extending a path is O(1).
 * Each step is either a member name ({@link String}) or an array index ({@link Integer}).
 */
public final class Location {

    public static final Location ROOT = new Location(null, null);

    private final @Nullable Location parent;
    private final @Nullable Object step;
    private final int depth;

    private Location(@Nullable Location parent, @Nullable Object step) {
        this.parent = parent;
        this.step = step;
        this.depth = parent == null ? 0 : parent.depth + 1;
    }

    /** Returns the number of steps from the root; the root has depth 0. */
    public int depth() {
        return depth;
    }

    public Location child(String name) {
        return new Location(this, name);
    }

    public Location child(int index) {
        return new Location(this, index);
    }

    /** Returns the steps from the root, each a {@link String} name or an {@link Integer} index. */
    public List<Object> steps() {
        List<Object> steps = new ArrayList<>();
        for (Location l = this; l.parent != null; l = l.parent) {
            steps.add(l.step);
        }
        Collections.reverse(steps);
        return Collections.unmodifiableList(steps);
    }

    /** Returns the location as a normalized path (RFC 9535, section 2.7). */
    public NormalizedPath toNormalizedPath() {
        List<NormalizedPath.Step> steps = new ArrayList<>(depth);
        for (Object step : steps()) {
            steps.add(
                    step instanceof Integer index
                            ? new NormalizedPath.Index(index)
                            : new NormalizedPath.Name((String) step));
        }
        return NormalizedPath.of(steps);
    }

    /** Returns the normalized path as a string, for example {@code $['store']['book'][0]}. */
    public String normalizedPath() {
        return toNormalizedPath().toString();
    }

    @Override
    public String toString() {
        return normalizedPath();
    }
}
