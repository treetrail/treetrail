package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Located;
import io.github.treetrail.jsonpath.internal.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The location of a node in a document as a normalized path (RFC 9535, section 2.7): the member names and
 * array indices that lead from the root to the node, for example {@code $['store']['book'][0]}.
 *
 * <pre>{@code
 * NormalizedPath path = NormalizedPath.parse("$['store']['book'][0]");
 * path.steps();          // [Name[name=store], Name[name=book], Index[index=0]]
 * path.toJsonPointer();  // "/store/book/0"
 * }</pre>
 *
 * <p>Every selected {@link Node} has one ({@link Node#normalizedPath()}). Instances are immutable; two paths
 * are equal if they have the same steps.
 *
 * <p>Paths sort in document order ({@link #compareTo}), and {@link #resolve} finds the node at a path again, for
 * example to change a document at the nodes a query selected:
 *
 * <pre>{@code
 * List<NormalizedPath> paths = new ArrayList<>(
 *         JsonPath.compile("$..[?@.obsolete]").query(document, model).distinctPaths());
 * // Later elements and inner nodes first, so that the paths of the others stay valid.
 * paths.sort(Comparator.reverseOrder());
 * for (NormalizedPath path : paths) {
 *     Node<N> parent = path.parent().orElseThrow().resolve(document, model).orElseThrow();
 *     // remove path.lastStep() from parent.value()
 * }
 * }</pre>
 */
public final class NormalizedPath implements Comparable<NormalizedPath> {

    /** A step of a normalized path: a member {@link Name} or an array {@link Index}. */
    public sealed interface Step permits Name, Index {}

    /**
     * A member name.
     *
     * @param name the member name, any string
     */
    public record Name(String name) implements Step {

        /** Creates the step. */
        public Name {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * An array index.
     *
     * @param index the index, 0 or more
     */
    public record Index(int index) implements Step {

        /**
         * Creates the step.
         *
         * @throws IllegalArgumentException if {@code index} is negative
         */
        public Index {
            if (index < 0) {
                throw new IllegalArgumentException("Array index must not be negative: " + index);
            }
        }
    }

    private static final NormalizedPath ROOT = new NormalizedPath(List.of());

    private final List<Step> steps;
    // Computed on first use; a race only computes the same string twice.
    private @Nullable String string;

    private NormalizedPath(List<Step> steps) {
        this.steps = steps;
    }

    /** Returns the path of the root node, {@code $}. */
    public static NormalizedPath root() {
        return ROOT;
    }

    /** Returns the path made of these steps; no steps is the root. */
    public static NormalizedPath of(List<? extends Step> steps) {
        List<Step> copy = List.copyOf(steps);
        return copy.isEmpty() ? ROOT : new NormalizedPath(copy);
    }

    /**
     * Parses a normalized path, as {@link Node#path()} returns it. Only the normalized form is accepted: member
     * names in single quotes with the escapes of RFC 9535, section 2.7, and indices without sign or leading zeros.
     *
     * @throws IllegalArgumentException if {@code normalizedPath} is not a normalized path
     * @throws NullPointerException if {@code normalizedPath} is null
     */
    public static NormalizedPath parse(String normalizedPath) {
        return new Reader(Objects.requireNonNull(normalizedPath, "normalizedPath")).read();
    }

    /** Returns the steps from the root to the node. */
    public List<Step> steps() {
        return steps;
    }

    /** Returns whether this is the path of the root, {@code $}. */
    public boolean isRoot() {
        return steps.isEmpty();
    }

    /** Returns the path of the node that contains the node at this path, or empty for the root. */
    public Optional<NormalizedPath> parent() {
        return steps.isEmpty() ? Optional.empty() : Optional.of(of(steps.subList(0, steps.size() - 1)));
    }

    /**
     * Returns the last step: the member name or index of the node in its parent, or empty for the root. With
     * {@link #parent()}, it says where to change the parent to replace or remove the node.
     */
    public Optional<Step> lastStep() {
        return steps.isEmpty() ? Optional.empty() : Optional.of(steps.get(steps.size() - 1));
    }

    /**
     * Returns whether this path is {@code ancestor} or leads to a node inside it, that is, whether its steps
     * begin with the steps of {@code ancestor}. Every path starts with the root.
     */
    public boolean startsWith(NormalizedPath ancestor) {
        List<Step> prefix = ancestor.steps;
        return prefix.size() <= steps.size() && steps.subList(0, prefix.size()).equals(prefix);
    }

    /**
     * Finds the node at this path in a document, or returns empty if the document has no node there: a member
     * is missing, an index is out of bounds, or a step meets a value that is not an object or array. The node is
     * returned even if its value is JSON {@code null}; see {@link NodeList#single()}.
     *
     * <pre>{@code
     * NormalizedPath.parse("$['store']['book'][0]").resolve(document, Jackson2Model.INSTANCE)
     *         .ifPresent(node -> ...);
     * }</pre>
     *
     * @param <N> the node type of the object model
     */
    public <N extends @Nullable Object> Optional<Node<N>> resolve(N document, JsonModel<N> model) {
        Objects.requireNonNull(model, "model");
        N value = document;
        Located location = Located.root(document);
        for (Step step : steps) {
            if (step instanceof Name name) {
                if (model.kind(value) != JsonKind.OBJECT || !model.hasMember(value, name.name())) {
                    return Optional.empty();
                }
                value = model.member(value, name.name());
                location = location.child(value, name.name());
            } else {
                int index = ((Index) step).index();
                if (model.kind(value) != JsonKind.ARRAY || index >= model.size(value)) {
                    return Optional.empty();
                }
                value = model.element(value, index);
                location = location.child(value, index);
            }
        }
        return Optional.of(NodeList.of(location));
    }

    /**
     * Compares paths in document order, the order in which a query visits the nodes of a document: a node
     * before the nodes inside it, and array elements by index. So among the paths of one document, a reversed
     * order removes nodes safely: later array elements before earlier ones, and nodes inside a node before the
     * node itself. Member names have no order in JSON; they are ordered by their Unicode scalar values (as
     * strings compare in filters), so that the order is total and consistent with {@link #equals}. Where two
     * paths have an index and a name at the same step, which no two paths of one document have, the index
     * comes first.
     */
    @Override
    public int compareTo(NormalizedPath other) {
        List<Step> a = steps;
        List<Step> b = other.steps;
        int common = Math.min(a.size(), b.size());
        for (int i = 0; i < common; i++) {
            int c = compare(a.get(i), b.get(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static int compare(Step a, Step b) {
        if (a instanceof Index i) {
            return b instanceof Index j ? Integer.compare(i.index(), j.index()) : -1;
        }
        return b instanceof Name n ? Values.compareCodePoints(((Name) a).name(), n.name()) : 1;
    }

    /** Returns the path to the member with the given name of the node at this path. */
    public NormalizedPath child(String name) {
        return append(new Name(name));
    }

    /**
     * Returns the path to the element with the given index of the node at this path.
     *
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public NormalizedPath child(int index) {
        return append(new Index(index));
    }

    private NormalizedPath append(Step step) {
        List<Step> longer = new ArrayList<>(steps.size() + 1);
        longer.addAll(steps);
        longer.add(step);
        return new NormalizedPath(List.copyOf(longer));
    }

    /**
     * Returns the path as a JSON Pointer (RFC 6901), for example {@code /store/book/0}; the root is the empty
     * string. {@code ~} and {@code /} in member names become {@code ~0} and {@code ~1}.
     */
    public String toJsonPointer() {
        StringBuilder sb = new StringBuilder();
        for (Step step : steps) {
            sb.append('/');
            if (step instanceof Index index) {
                sb.append(index.index());
            } else {
                String name = ((Name) step).name();
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (c == '~') {
                        sb.append("~0");
                    } else if (c == '/') {
                        sb.append("~1");
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Returns the normalized path, for example {@code $['store']['book'][0]}. */
    @Override
    public String toString() {
        String s = string;
        if (s == null) {
            StringBuilder sb = new StringBuilder("$");
            for (Step step : steps) {
                if (step instanceof Index index) {
                    Located.appendIndex(sb, index.index());
                } else {
                    Located.appendName(sb, ((Name) step).name());
                }
            }
            s = sb.toString();
            string = s;
        }
        return s;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof NormalizedPath other && steps.equals(other.steps);
    }

    @Override
    public int hashCode() {
        // The text is canonical, so it hashes consistently with equals; Node hashes the same text.
        return toString().hashCode();
    }

    /** Parser for the normalized-path grammar of RFC 9535, section 2.7. */
    private static final class Reader {

        private final String text;
        private int pos;

        Reader(String text) {
            this.text = text;
        }

        NormalizedPath read() {
            expect('$');
            List<Step> steps = new ArrayList<>();
            while (pos < text.length()) {
                expect('[');
                if (pos < text.length() && text.charAt(pos) == '\'') {
                    steps.add(new Name(name()));
                } else {
                    steps.add(new Index(index()));
                }
                expect(']');
            }
            return of(steps);
        }

        private String name() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("Unterminated member name");
                }
                char c = text.charAt(pos);
                if (c == '\'') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    sb.append(escape());
                } else if (c < 0x20) {
                    throw error("Control characters must be escaped");
                } else if (Character.isHighSurrogate(c)
                        && pos + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(pos + 1))) {
                    sb.append(c).append(text.charAt(pos + 1));
                    pos += 2;
                } else if (Character.isSurrogate(c)) {
                    throw error("Unpaired surrogate");
                } else {
                    sb.append(c);
                    pos++;
                }
            }
        }

        private char escape() {
            if (pos >= text.length()) {
                throw error("Unterminated escape");
            }
            char c = text.charAt(pos++);
            return switch (c) {
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '\'' -> '\'';
                case '\\' -> '\\';
                case 'u' -> unicodeEscape();
                default -> throw error("Invalid escape \\" + c);
            };
        }

        /** {@code \}{@code u00XX} for the control characters that have no short escape, lower-case hex. */
        private char unicodeEscape() {
            if (pos + 4 > text.length() || !text.startsWith("00", pos)) {
                throw error("Invalid \\u escape");
            }
            int high = text.charAt(pos + 2) - '0';
            int low = Character.digit(text.charAt(pos + 3), 16);
            boolean lowerCase = !Character.isUpperCase(text.charAt(pos + 3));
            if ((high != 0 && high != 1) || low < 0 || !lowerCase) {
                throw error("Invalid \\u escape");
            }
            char c = (char) (high * 16 + low);
            if (c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t') {
                throw error("Use the short escape for this character");
            }
            pos += 4;
            return c;
        }

        private int index() {
            int start = pos;
            while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                pos++;
            }
            if (pos == start) {
                throw error("Expected a member name in single quotes or an array index");
            }
            if (text.charAt(start) == '0' && pos - start > 1) {
                throw error("Array index with a leading zero");
            }
            try {
                return Integer.parseInt(text, start, pos, 10);
            } catch (NumberFormatException e) {
                throw error("Array index too large");
            }
        }

        private void expect(char c) {
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String reason) {
            return new IllegalArgumentException(
                    "Not a normalized path: " + reason + " at position " + pos + " in " + text);
        }
    }
}
