package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.EvaluationLimits;
import io.github.treetrail.jsonpath.FunctionType;
import io.github.treetrail.jsonpath.FunctionValue;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import io.github.treetrail.jsonpath.JsonPathEvaluationException;
import io.github.treetrail.jsonpath.JsonPathLimitExceededException;
import io.github.treetrail.jsonpath.internal.Ast.And;
import io.github.treetrail.jsonpath.internal.Ast.Argument;
import io.github.treetrail.jsonpath.internal.Ast.Comparison;
import io.github.treetrail.jsonpath.internal.Ast.Expr;
import io.github.treetrail.jsonpath.internal.Ast.Filter;
import io.github.treetrail.jsonpath.internal.Ast.FunctionCall;
import io.github.treetrail.jsonpath.internal.Ast.Index;
import io.github.treetrail.jsonpath.internal.Ast.Literal;
import io.github.treetrail.jsonpath.internal.Ast.Name;
import io.github.treetrail.jsonpath.internal.Ast.Not;
import io.github.treetrail.jsonpath.internal.Ast.Operand;
import io.github.treetrail.jsonpath.internal.Ast.Or;
import io.github.treetrail.jsonpath.internal.Ast.Paren;
import io.github.treetrail.jsonpath.internal.Ast.Query;
import io.github.treetrail.jsonpath.internal.Ast.QueryOperand;
import io.github.treetrail.jsonpath.internal.Ast.Segment;
import io.github.treetrail.jsonpath.internal.Ast.Selector;
import io.github.treetrail.jsonpath.internal.Ast.Slice;
import io.github.treetrail.jsonpath.internal.Ast.Test;
import io.github.treetrail.jsonpath.internal.Ast.Wildcard;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.Arguments;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.LogicalFunction;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.NodesFunction;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.ValueFunction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Executes a parsed query against a document (RFC 9535, sections 2.3 to 2.5).
 *
 * <p>The evaluator represents nodes as {@code L}, as {@link Nodes} creates them: {@link #LOCATED} keeps each node's
 * location, {@link #VALUES} only its value, so that a query for values allocates nothing per visited node. Depths are
 * passed along separately, for {@link EvaluationLimits#maxDepth()}.
 *
 * <p>An evaluator runs one query once. Absolute queries inside filters ({@code $[?@.a == $.b]}) do not
 * depend on the current node, so each is evaluated once per run and its result reused; otherwise
 * nesting them would multiply the work by the document size per level. Every node visit counts
 * against {@link EvaluationLimits#maxVisitedNodes()}.
 */
public final class Evaluator<L extends @Nullable Object> {

    /** The thread's interrupt status is checked whenever the visit count passes a multiple of 2^12 = 4,096. */
    private static final int INTERRUPT_CHECK_SHIFT = 12;

    /** How the evaluator represents the nodes of the document. */
    public interface Nodes<L extends @Nullable Object> {

        L root(@Nullable Object document);

        L child(L parent, @Nullable Object value, String name);

        L child(L parent, @Nullable Object value, int index);

        @Nullable
        Object value(L node);

        /** The normalized path of a node, or null if the nodes do not know their locations. */
        @Nullable
        String path(L node);
    }

    /** Nodes with their locations. */
    public static final Nodes<Located> LOCATED = new Nodes<>() {
        @Override
        public Located root(@Nullable Object document) {
            return Located.root(document);
        }

        @Override
        public Located child(Located parent, @Nullable Object value, String name) {
            return parent.child(value, name);
        }

        @Override
        public Located child(Located parent, @Nullable Object value, int index) {
            return parent.child(value, index);
        }

        @Override
        public @Nullable Object value(Located node) {
            return node.value();
        }

        @Override
        public String path(Located node) {
            return node.normalizedPath();
        }
    };

    /** Nodes as their bare values, without locations: nothing is allocated per node. */
    public static final Nodes<@Nullable Object> VALUES = new Nodes<>() {
        @Override
        public @Nullable Object root(@Nullable Object document) {
            return document;
        }

        @Override
        public @Nullable Object child(@Nullable Object parent, @Nullable Object value, String name) {
            return value;
        }

        @Override
        public @Nullable Object child(@Nullable Object parent, @Nullable Object value, int index) {
            return value;
        }

        @Override
        public @Nullable Object value(@Nullable Object node) {
            return node;
        }

        @Override
        public @Nullable String path(@Nullable Object node) {
            return null;
        }
    };

    /**
     * Thrown instead of a failure at a node when the nodes do not know their locations; the caller runs the query
     * again with {@link #LOCATED} nodes, which throws the failure with the node's path. Without a stack trace, as it
     * never leaves the library.
     */
    public static final class LocationNeeded extends JsonPathEvaluationException {

        private static final long serialVersionUID = 1L;

        LocationNeeded() {
            super("The location of the failure is needed");
            setStackTrace(new StackTraceElement[0]);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /** Returned by {@link #firstFrom} when the query selects no node, since a selected node may be null. */
    private enum None {
        NONE
    }

    private final JsonModel<@Nullable Object> model;
    private final Nodes<L> nodes;
    private final L root;
    private final long maxVisitedNodes;
    private final int maxResultSize;
    private final int maxDepth;
    private long visitedNodes;

    /** Results of absolute queries inside filters, created on first use. */
    private @Nullable IdentityHashMap<Query, List<L>> absoluteNodes;

    private @Nullable IdentityHashMap<Query, List<Val>> absoluteValues;

    @SuppressWarnings("unchecked")
    public Evaluator(@Nullable Object document, JsonModel<?> model, EvaluationLimits limits, Nodes<L> nodes) {
        this.model = (JsonModel<@Nullable Object>) model;
        this.nodes = nodes;
        this.root = nodes.root(document);
        this.maxVisitedNodes = limits.maxVisitedNodes();
        this.maxResultSize = limits.maxResultSize();
        this.maxDepth = limits.maxDepth();
    }

    /** Receives the nodes a query selects, in order; returns false to stop the evaluation. */
    private interface Sink<L extends @Nullable Object> {
        boolean accept(L node);
    }

    /** Runs the query and returns the nodes it selects. */
    public List<L> run(Query query) {
        List<L> result = new ArrayList<>();
        evaluate(query, root, 0, node -> {
            if (result.size() >= maxResultSize) {
                throw new JsonPathLimitExceededException(
                        "Query selected more than " + maxResultSize + " nodes, the limit");
            }
            result.add(node);
            return true;
        });
        return result;
    }

    /** Whether the query selects at least one node; stops at the first. */
    public boolean exists(Query query) {
        boolean[] found = {false};
        evaluate(query, root, 0, node -> {
            found[0] = true;
            return false;
        });
        return found[0];
    }

    /** Returns the first node the query selects, or null; stops there. Only for {@link #LOCATED} nodes. */
    public @Nullable L first(Query query) {
        Object first = firstFrom(query, root, 0);
        return first instanceof None ? null : node(first);
    }

    /** Casts a node kept in an {@code Object} slot; null only where L includes null, which NullAway cannot tell. */
    @SuppressWarnings({"unchecked", "NullAway"})
    private L node(@Nullable Object node) {
        return (L) node;
    }

    /**
     * Counts node visits against the limit, before the nodes are visited, and checks for interruption
     * now and then. Kept small so that it inlines into the selection loops.
     */
    private void visit(int count) {
        long before = visitedNodes;
        long after = before + count;
        visitedNodes = after;
        if (after > maxVisitedNodes || (before >>> INTERRUPT_CHECK_SHIFT) != (after >>> INTERRUPT_CHECK_SHIFT)) {
            checkLimits(after);
        }
    }

    /** The exception for documents nested deeper than the limit, which includes documents with cycles. */
    static JsonPathLimitExceededException tooDeep(int maxDepth) {
        return new JsonPathLimitExceededException("Document is nested more than " + maxDepth
                + " levels deep; with plain Java objects, it may contain itself");
    }

    private void checkLimits(long visited) {
        if (visited > maxVisitedNodes) {
            throw new JsonPathLimitExceededException("Query visited more than " + maxVisitedNodes + " nodes");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new JsonPathEvaluationException("Query evaluation was interrupted");
        }
    }

    /** Runs a query inside a filter; absolute queries are evaluated once per run. */
    private List<L> query(Query query, L current, int depth) {
        if (!query.absolute()) {
            List<L> selected = new ArrayList<>();
            evaluate(query, current, depth, selected::add);
            return selected;
        }
        if (absoluteNodes == null) {
            absoluteNodes = new IdentityHashMap<>();
        }
        List<L> selected = absoluteNodes.get(query);
        if (selected == null) {
            List<L> evaluated = new ArrayList<>();
            evaluate(query, root, 0, evaluated::add);
            selected = evaluated;
            absoluteNodes.put(query, selected);
        }
        return selected;
    }

    /**
     * The first node a query inside a filter selects, or {@link None#NONE}; absolute queries use their result of this
     * run.
     */
    private @Nullable Object firstOf(Query query, L current, int depth) {
        if (query.absolute()) {
            List<L> selected = query(query, current, depth);
            return selected.isEmpty() ? None.NONE : selected.get(0);
        }
        return firstFrom(query, current, depth);
    }

    /** Evaluates a query from a start node until it selects the first node; returns it or {@link None#NONE}. */
    private @Nullable Object firstFrom(Query query, L start, int depth) {
        @Nullable Object[] first = {None.NONE};
        evaluate(query, start, depth, node -> {
            first[0] = node;
            return false;
        });
        return first[0];
    }

    /**
     * Evaluates a query from a start node and passes each selected node to the sink, in order. The nodes
     * go through the segments depth first: each node a segment selects is passed on to the next segment
     * before the segment selects the next one. This keeps the order of RFC 9535 (the result of a segment
     * is the concatenation of its results for each input node) without building a list per segment, and
     * lets the sink stop the evaluation early.
     */
    private void evaluate(Query query, L start, int depth, Sink<L> sink) {
        List<Segment> segments = query.segments();
        apply(segments, 0, start, depth, sink);
    }

    /** Applies the segments from {@code index} on to one node; returns false if the sink stopped. */
    private boolean apply(List<Segment> segments, int index, L node, int depth, Sink<L> sink) {
        if (index == segments.size()) {
            return sink.accept(node);
        }
        Segment segment = segments.get(index);
        if (!segment.descendant()) {
            return select(segment, node, depth, null, 0, 0, segments, index + 1, sink);
        }
        // Descendant segment: the node and all its descendants in document order (each node before its
        // children, children in order), each with the segment's selectors. The nodes still to visit are on one
        // stack, the next on top. A node's children are pushed in order, so that wildcard and filter selectors
        // take them from there, and then reversed, so that the first is visited next; no list per node.
        NodeStack stack = new NodeStack();
        stack.push(node);
        stack.startRun(0, depth);
        while (stack.size > 0) {
            L current = node(stack.pop());
            int currentDepth = stack.depth();
            int from = stack.size;
            pushChildren(current, currentDepth, stack);
            int to = stack.size;
            if (to > from) {
                stack.startRun(from, currentDepth + 1);
            }
            if (!select(segment, current, currentDepth, stack.nodes, from, to, segments, index + 1, sink)) {
                return false;
            }
            stack.reverse(from, to);
        }
        return true;
    }

    /**
     * The nodes a descendant segment still has to visit. A popped node's slot is overwritten by later pushes; the
     * stack lives for one walk only. The children of a node lie together on the stack and have the same depth, so
     * depths are kept per run of nodes, in a second stack that grows with the depth of the document, not with the
     * number of nodes: run {@code r} starts at {@code runStarts[r]} and its nodes are {@code runDepths[r]} deep.
     */
    private static final class NodeStack {

        @Nullable
        Object[] nodes = new Object[16];

        int size;
        int[] runStarts = new int[8];
        int[] runDepths = new int[8];
        int runs;

        void push(@Nullable Object node) {
            if (size == nodes.length) {
                nodes = Arrays.copyOf(nodes, size * 2);
            }
            nodes[size++] = node;
        }

        @Nullable
        Object pop() {
            int top = --size;
            // Runs that start above the popped node have been visited completely.
            while (runStarts[runs - 1] > top) {
                runs--;
            }
            return nodes[top];
        }

        /** The depth of the node popped last. */
        int depth() {
            return runDepths[runs - 1];
        }

        /** Starts a run of nodes at {@code start}, the current size before they were pushed. */
        void startRun(int start, int depth) {
            if (runs == runStarts.length) {
                runStarts = Arrays.copyOf(runStarts, runs * 2);
                runDepths = Arrays.copyOf(runDepths, runs * 2);
            }
            runStarts[runs] = start;
            runDepths[runs++] = depth;
        }

        /** Reverses the nodes in {@code [from, to)}, which all have the same depth. */
        void reverse(int from, int to) {
            for (int i = from, j = to - 1; i < j; i++, j--) {
                Object node = nodes[i];
                nodes[i] = nodes[j];
                nodes[j] = node;
            }
        }
    }

    /**
     * Applies a segment's selectors to a node and passes what they select on to the next segment.
     * {@code children[from, to)} are the node's children if the caller has computed them already, else
     * {@code children} is null.
     */
    private boolean select(
            Segment segment,
            L node,
            int depth,
            @Nullable Object @Nullable [] children,
            int from,
            int to,
            List<Segment> segments,
            int next,
            Sink<L> sink) {
        for (Selector selector : segment.selectors()) {
            if (!select(selector, node, depth, children, from, to, segments, next, sink)) {
                return false;
            }
        }
        return true;
    }

    /** Pushes the children of a node, in order, for the walk of a descendant segment. */
    private void pushChildren(L node, int depth, NodeStack stack) {
        try {
            Object value = nodes.value(node);
            JsonKind kind = model.kind(value);
            if (kind == JsonKind.OBJECT) {
                checkDepth(depth);
                visit(model.memberCount(value));
                for (Map.Entry<String, Object> member : model.members(value)) {
                    stack.push(nodes.child(node, member.getValue(), member.getKey()));
                }
            } else if (kind == JsonKind.ARRAY) {
                checkDepth(depth);
                int size = model.size(value);
                visit(size);
                for (int i = 0; i < size; i++) {
                    stack.push(nodes.child(node, model.element(value, i), i));
                }
            }
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /** Fails for children of a node at the depth limit. */
    private void checkDepth(int depth) {
        if (depth >= maxDepth) {
            throw tooDeep(maxDepth);
        }
    }

    /**
     * Wraps an exception from the model or a function, typically a value that is not JSON, with the
     * location of the node being processed. Exceptions of nested queries and of later segments carry
     * their own, closer node. Without locations, {@link LocationNeeded}.
     */
    private JsonPathEvaluationException modelFailure(RuntimeException e, L node) {
        String path = nodes.path(node);
        if (path == null) {
            return new LocationNeeded();
        }
        String reason = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
        return new JsonPathEvaluationException(reason, path, e);
    }

    private boolean select(
            Selector selector,
            L node,
            int depth,
            @Nullable Object @Nullable [] children,
            int from,
            int to,
            List<Segment> segments,
            int next,
            Sink<L> sink) {
        if (selector instanceof Wildcard || selector instanceof Filter) {
            Expr expr = selector instanceof Filter filter ? filter.expr() : null;
            if (children == null) {
                return selectChildren(node, depth, expr, segments, next, sink);
            }
            // Produced again by this selector: counted like a second visit, as when computed again.
            visit(to - from);
            for (int i = from; i < to; i++) {
                if (!selectChild(node(children[i]), depth + 1, expr, segments, next, sink)) {
                    return false;
                }
            }
            return true;
        }
        if (!(selector instanceof Name nameSelector)) {
            // Index and slice selectors in their own methods, so that this one stays small enough for the JIT
            // compiler to inline it.
            return selector instanceof Index index
                    ? selectIndex(index, node, depth, segments, next, sink)
                    : selectSlice((Slice) selector, node, depth, segments, next, sink);
        }
        L selected;
        try {
            Object value = nodes.value(node);
            if (model.kind(value) != JsonKind.OBJECT) {
                return true;
            }
            String name = nameSelector.name();
            Object member = model.findMember(value, name);
            if (member == null && !model.hasMember(value, name)) {
                return true;
            }
            visit(1);
            selected = nodes.child(node, member, name);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
        return apply(segments, next, selected, depth + 1, sink);
    }

    /** Index selector: passes the element at the index on, if the node is an array that has it. */
    private boolean selectIndex(Index selector, L node, int depth, List<Segment> segments, int next, Sink<L> sink) {
        L selected;
        try {
            Object value = nodes.value(node);
            if (model.kind(value) != JsonKind.ARRAY) {
                return true;
            }
            int size = model.size(value);
            long index = selector.index();
            long normalized = index >= 0 ? index : size + index;
            if (normalized < 0 || normalized >= size) {
                return true;
            }
            int i = (int) normalized;
            visit(1);
            selected = nodes.child(node, model.element(value, i), i);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
        return apply(segments, next, selected, depth + 1, sink);
    }

    /** Slice selector: passes the selected elements on, if the node is an array. */
    private boolean selectSlice(Slice selector, L node, int depth, List<Segment> segments, int next, Sink<L> sink) {
        long length;
        try {
            Object value = nodes.value(node);
            if (model.kind(value) != JsonKind.ARRAY) {
                return true;
            }
            length = model.size(value);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
        return slice(selector, node, depth, length, segments, next, sink);
    }

    /**
     * Selects the children of a node that pass a filter, or all of them for a wildcard ({@code filter} null),
     * one by one as the model produces them.
     */
    private boolean selectChildren(
            L node, int depth, @Nullable Expr filter, List<Segment> segments, int next, Sink<L> sink) {
        Object value = nodes.value(node);
        Iterator<Map.Entry<String, @Nullable Object>> members;
        int size;
        try {
            JsonKind kind = model.kind(value);
            if (kind == JsonKind.OBJECT) {
                checkDepth(depth);
                visit(model.memberCount(value));
                members = model.members(value).iterator();
                size = 0;
            } else if (kind == JsonKind.ARRAY) {
                checkDepth(depth);
                size = model.size(value);
                visit(size);
                members = null;
            } else {
                return true;
            }
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
        if (members != null) {
            while (hasNext(node, members)) {
                if (!selectChild(nextMember(node, members), depth + 1, filter, segments, next, sink)) {
                    return false;
                }
            }
            return true;
        }
        for (int i = 0; i < size; i++) {
            if (!selectChild(element(node, i), depth + 1, filter, segments, next, sink)) {
                return false;
            }
        }
        return true;
    }

    /** Passes a child on to the next segment if it passes the filter, if any. */
    private boolean selectChild(
            L child, int depth, @Nullable Expr filter, List<Segment> segments, int next, Sink<L> sink) {
        return (filter != null && !matches(filter, child, depth)) || apply(segments, next, child, depth, sink);
    }

    /** Whether an object node has another member. */
    private boolean hasNext(L node, Iterator<Map.Entry<String, @Nullable Object>> members) {
        try {
            return members.hasNext();
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /** The next member of an object node, which has one. */
    private L nextMember(L node, Iterator<Map.Entry<String, @Nullable Object>> members) {
        try {
            Map.Entry<String, @Nullable Object> member = members.next();
            return nodes.child(node, member.getValue(), member.getKey());
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /** The element of an array node at an index that exists. */
    private L element(L node, int index) {
        try {
            return nodes.child(node, model.element(nodes.value(node), index), index);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /** Tests a filter expression on a candidate node; failures carry the candidate's location. */
    private boolean matches(Expr expr, L child, int depth) {
        try {
            return test(expr, child, depth);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, child);
        }
    }

    /** Array slice (RFC 9535, section 2.3.4.2): passes the selected elements of an array of length {@code len} on. */
    private boolean slice(Slice slice, L node, int depth, long len, List<Segment> segments, int next, Sink<L> sink) {
        long step = slice.step() == null ? 1 : slice.step();
        if (step == 0) {
            return true;
        }
        long start = slice.start() != null ? slice.start() : (step >= 0 ? 0 : len - 1);
        long end = slice.end() != null ? slice.end() : (step >= 0 ? len : -len - 1);
        long nStart = start >= 0 ? start : len + start;
        long nEnd = end >= 0 ? end : len + end;
        if (step > 0) {
            long lower = Math.min(Math.max(nStart, 0), len);
            long upper = Math.min(Math.max(nEnd, 0), len);
            for (long i = lower; i < upper; i += step) {
                if (!sliced(node, depth, (int) i, segments, next, sink)) {
                    return false;
                }
            }
        } else {
            long upper = Math.min(Math.max(nStart, -1), len - 1);
            long lower = Math.min(Math.max(nEnd, -1), len - 1);
            for (long i = upper; lower < i; i += step) {
                if (!sliced(node, depth, (int) i, segments, next, sink)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean sliced(L node, int depth, int index, List<Segment> segments, int next, Sink<L> sink) {
        visit(1);
        return apply(segments, next, element(node, index), depth + 1, sink);
    }

    // ---- logical expressions ----

    private boolean test(Expr expr, L current, int depth) {
        if (expr instanceof Or or) {
            for (Expr operand : or.operands()) {
                if (test(operand, current, depth)) {
                    return true;
                }
            }
            return false;
        }
        if (expr instanceof And and) {
            for (Expr operand : and.operands()) {
                if (!test(operand, current, depth)) {
                    return false;
                }
            }
            return true;
        }
        if (expr instanceof Not not) {
            return !test(not.operand(), current, depth);
        }
        if (expr instanceof Paren paren) {
            return test(paren.operand(), current, depth);
        }
        if (expr instanceof Comparison comparison) {
            return Values.compare(
                    value(comparison.left(), current, depth),
                    comparison.op(),
                    value(comparison.right(), current, depth),
                    maxDepth);
        }
        Operand operand = ((Test) expr).operand();
        return logical(operand, current, depth);
    }

    /** Converts a query or function result to a logical value (NodesType: non-empty). */
    private boolean logical(Operand operand, L current, int depth) {
        if (operand instanceof QueryOperand query) {
            return !(firstOf(query.query(), current, depth) instanceof None);
        }
        FunctionCall call = (FunctionCall) operand;
        FunctionDefinition.Implementation implementation = call.function().implementation();
        if (implementation instanceof LogicalFunction function) {
            return function.body().test(arguments(call, current, depth));
        }
        return !((NodesFunction) implementation)
                .body()
                .apply(arguments(call, current, depth))
                .isEmpty();
    }

    private Val value(Operand operand, L current, int depth) {
        if (operand instanceof Literal literal) {
            return literal.val();
        }
        if (operand instanceof QueryOperand query) {
            Object first = firstOf(query.query(), current, depth);
            return first instanceof None ? Val.NOTHING : Val.of(nodes.value(node(first)), model);
        }
        FunctionCall call = (FunctionCall) operand;
        FunctionDefinition function = call.function();
        return Val.from(
                ((ValueFunction) function.implementation()).body().apply(arguments(call, current, depth)),
                function.name());
    }

    private List<Val> nodes(Operand operand, L current, int depth) {
        if (operand instanceof QueryOperand queryOperand) {
            Query query = queryOperand.query();
            if (!query.absolute()) {
                return values(query(query, current, depth));
            }
            // Converted once as well, so that count($..*) in a filter costs O(1) per node.
            if (absoluteValues == null) {
                absoluteValues = new IdentityHashMap<>();
            }
            List<Val> values = absoluteValues.get(query);
            if (values == null) {
                values = Collections.unmodifiableList(values(query(query, current, depth)));
                absoluteValues.put(query, values);
            }
            return values;
        }
        FunctionCall call = (FunctionCall) operand;
        FunctionDefinition function = call.function();
        List<FunctionValue> result =
                ((NodesFunction) function.implementation()).body().apply(arguments(call, current, depth));
        List<Val> values = new ArrayList<>(result.size());
        for (FunctionValue value : result) {
            values.add(Val.from(value, function.name()));
        }
        return values;
    }

    // NullAway does not see that the elements of a List<L> may be null when L is a nullable type variable.
    @SuppressWarnings("NullAway")
    private List<Val> values(List<L> selected) {
        List<Val> values = new ArrayList<>(selected.size());
        for (L node : selected) {
            values.add(Val.of(nodes.value(node), model));
        }
        return values;
    }

    /** Evaluates the arguments of a call as the declared parameter types require (RFC 9535, section 2.4.3). */
    private Arguments arguments(FunctionCall call, L current, int depth) {
        List<FunctionType> parameters = call.function().parameters();
        Object[] args = new Object[parameters.size()];
        for (int i = 0; i < args.length; i++) {
            Argument argument = call.arguments().get(i);
            args[i] = switch (parameters.get(i)) {
                case VALUE -> value((Operand) argument, current, depth);
                case LOGICAL ->
                    argument instanceof Expr expr
                            ? test(expr, current, depth)
                            : logical((Operand) argument, current, depth);
                case NODES -> nodes((Operand) argument, current, depth);
            };
        }
        return new Arguments(args);
    }
}
