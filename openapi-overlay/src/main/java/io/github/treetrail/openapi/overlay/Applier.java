package io.github.treetrail.openapi.overlay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.Node;
import io.github.treetrail.jsonpath.NormalizedPath;
import io.github.treetrail.jsonpath.jackson2.Jackson2Model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Applies the actions of an overlay, in order, to one document tree, which it changes in place.
 *
 * <p>Every action first runs its target query on the document as the previous actions left it, then changes the
 * selected nodes, which it finds again by their normalized paths: Treetrail's nodes know their value and location,
 * not their parent. A node selected more than once, as by {@code $['a','a']}, is changed once.
 */
final class Applier {

    private JsonNode root;
    private final OverlayOptions options;
    private final List<OverlayChange> changes = new ArrayList<>();
    private final List<OverlayWarning> warnings = new ArrayList<>();

    Applier(JsonNode root, OverlayOptions options) {
        this.root = root;
        this.options = options;
    }

    OverlayResult apply(List<OverlayAction> actions) {
        for (OverlayAction action : actions) {
            apply(action);
        }
        return new OverlayResult(root, changes, warnings);
    }

    private void apply(OverlayAction action) {
        if (action.kind() == OverlayAction.Kind.NONE) {
            return;
        }
        List<NormalizedPath> targets = select(action.target());
        if (targets.isEmpty()) {
            // "If the target JSONPath expression selects zero nodes, the action succeeds without changing the
            // target document."
            String message = "target " + action.target() + " selects no node";
            if (options.failOnNoMatch()) {
                throw new OverlayException(location(action), message);
            }
            warnings.add(new OverlayWarning(action.index(), action.target().expression(), message));
            return;
        }
        switch (action.kind()) {
            case REMOVE -> remove(action, targets);
            case UPDATE -> merge(action, targets, nonNull(action.update()));
            case COPY -> merge(action, targets, copySource(action));
            case NONE -> throw new AssertionError();
        }
    }

    /** Returns the paths of the nodes the query selects, each once, in the query's order. */
    private List<NormalizedPath> select(JsonPath query) {
        return query.withLimits(options.limits())
                .query(root, Jackson2Model.INSTANCE)
                .distinctPaths();
    }

    private JsonNode get(NormalizedPath path) {
        // The paths come from a query on the current tree, so the nodes exist.
        return path.resolve(root, Jackson2Model.INSTANCE).orElseThrow().value();
    }

    private JsonNode copySource(OverlayAction action) {
        JsonPath copy = nonNull(action.copy());
        List<NormalizedPath> sources = select(copy);
        if (sources.size() != 1) {
            throw new OverlayException(
                    location(action), "copy " + copy + " must select exactly one node, but selects " + sources.size());
        }
        return get(sources.get(0));
    }

    private void remove(OverlayAction action, List<NormalizedPath> targets) {
        // Removing an array element moves the following ones, so remove in reverse document order: later
        // elements before earlier ones, and nodes inside a selected node before the node itself.
        List<NormalizedPath> ordered = new ArrayList<>(targets);
        ordered.sort(Collections.reverseOrder());
        for (NormalizedPath path : ordered) {
            if (path.isRoot()) {
                throw new OverlayException(location(action), "cannot remove the root of the document");
            }
            JsonNode parent = get(path.parent().orElseThrow());
            if (path.lastStep().orElseThrow() instanceof NormalizedPath.Index index) {
                ((ArrayNode) parent).remove(index.index());
            } else {
                ((ObjectNode) parent)
                        .remove(((NormalizedPath.Name) path.lastStep().orElseThrow()).name());
            }
        }
        // Report in the order the target selected the nodes.
        for (NormalizedPath path : targets) {
            changes.add(new OverlayChange(action.index(), path, OverlayChange.Kind.REMOVED));
        }
    }

    /**
     * Merges {@code value} into every target, with the rules of the Action Object: objects are merged, an array
     * value is concatenated with an array target and any other value appended to it, and a primitive replaces a
     * primitive.
     */
    private void merge(OverlayAction action, List<NormalizedPath> targets, JsonNode value) {
        if (!options.lenientMerge()) {
            Kind kind = Kind.of(get(targets.get(0)));
            for (NormalizedPath path : targets) {
                Kind other = Kind.of(get(path));
                if (other != kind) {
                    throw new OverlayException(
                            location(action),
                            "target selects " + kind.plural + " and " + other.plural + " (" + targets.get(0) + ", "
                                    + path + "); an update or copy needs all objects, all arrays or all primitives");
                }
            }
        }
        // Copy the value before merging: a copy source may be one of the targets or inside one.
        JsonNode source = value.deepCopy();
        for (NormalizedPath path : targets) {
            Optional<Node<JsonNode>> current = path.resolve(root, Jackson2Model.INSTANCE);
            if (current.isEmpty()) {
                // Only in lenient mode: an earlier target of this action that contained this one was replaced.
                warnings.add(new OverlayWarning(
                        action.index(),
                        action.target().expression(),
                        "target " + path + " is gone: an earlier target of this action that contained it was"
                                + " replaced"));
                continue;
            }
            JsonNode target = current.get().value();
            OverlayChange.Kind change;
            if (target.isArray()) {
                if (source.isArray()) {
                    for (JsonNode element : source) {
                        ((ArrayNode) target).add(element.deepCopy());
                    }
                    change = OverlayChange.Kind.CONCATENATED;
                } else {
                    ((ArrayNode) target).add(source.deepCopy());
                    change = OverlayChange.Kind.APPENDED;
                }
            } else if (Kind.of(target) == Kind.of(source)) {
                if (target.isObject()) {
                    mergeObject(action, path, (ObjectNode) target, (ObjectNode) source);
                    change = OverlayChange.Kind.MERGED;
                } else {
                    replace(path, source.deepCopy());
                    change = OverlayChange.Kind.REPLACED;
                }
            } else if (options.lenientMerge()) {
                replace(path, source.deepCopy());
                change = OverlayChange.Kind.REPLACED;
            } else {
                throw incompatible(action, path, target, source);
            }
            changes.add(new OverlayChange(action.index(), path, change));
        }
    }

    /** The recursive merge of the Action Object: only objects are merged member by member. */
    private void mergeObject(OverlayAction action, NormalizedPath path, ObjectNode target, ObjectNode source) {
        for (Map.Entry<String, JsonNode> member : source.properties()) {
            String name = member.getKey();
            JsonNode value = member.getValue();
            JsonNode existing = target.get(name);
            if (existing == null) {
                target.set(name, value.deepCopy());
                continue;
            }
            Kind existingKind = Kind.of(existing);
            if (existingKind != Kind.of(value)) {
                if (!options.lenientMerge()) {
                    throw incompatible(action, path.child(name), existing, value);
                }
                target.set(name, value.deepCopy());
                continue;
            }
            switch (existingKind) {
                case PRIMITIVE -> target.set(name, value.deepCopy());
                case ARRAY -> {
                    for (JsonNode element : value) {
                        ((ArrayNode) existing).add(element.deepCopy());
                    }
                }
                case OBJECT -> mergeObject(action, path.child(name), (ObjectNode) existing, (ObjectNode) value);
            }
        }
    }

    /** Replaces the node at the path, in its parent or, for the root, as the root. */
    private void replace(NormalizedPath path, JsonNode value) {
        if (path.isRoot()) {
            root = value;
            return;
        }
        JsonNode parent = get(path.parent().orElseThrow());
        if (path.lastStep().orElseThrow() instanceof NormalizedPath.Index index) {
            ((ArrayNode) parent).set(index.index(), value);
        } else {
            ((ObjectNode) parent).set(((NormalizedPath.Name) path.lastStep().orElseThrow()).name(), value);
        }
    }

    private static OverlayException incompatible(
            OverlayAction action, NormalizedPath path, JsonNode target, JsonNode value) {
        return new OverlayException(
                location(action),
                "cannot merge " + Kind.of(value).singular + " into the " + Kind.of(target).noun + " at " + path + " ("
                        + path.toJsonPointer() + ")");
    }

    private static String location(OverlayAction action) {
        return "/actions/" + action.index();
    }

    private static <T> T nonNull(@Nullable T value) {
        if (value == null) {
            throw new AssertionError();
        }
        return value;
    }

    /** The three kinds of values the merge rules distinguish; null is a primitive (RFC 8259, section 1). */
    private enum Kind {
        OBJECT("object", "an object", "objects"),
        ARRAY("array", "an array", "arrays"),
        PRIMITIVE("primitive value", "a primitive value", "primitives");

        final String noun;
        final String singular;
        final String plural;

        Kind(String noun, String singular, String plural) {
            this.noun = noun;
            this.singular = singular;
            this.plural = plural;
        }

        static Kind of(JsonNode node) {
            if (node.isObject()) {
                return OBJECT;
            }
            return node.isArray() ? ARRAY : PRIMITIVE;
        }
    }
}
