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
 * Executes a parsed query against a document like {@link Evaluator}, but for the values of the selected nodes
 * only: nodes are their bare values, without locations, so that nothing is allocated per visited node. Depths are
 * passed along separately, for {@link EvaluationLimits#maxDepth()}. A failure at a node throws
 * {@link LocationNeeded}; the caller runs the query again with an {@link Evaluator} for the failure with the node's
 * path.
 *
 * <p>Kept apart from {@link Evaluator} on purpose: one evaluator for both kinds of nodes is slower for queries with
 * locations, as its methods grow too big for the JIT compiler to inline them. A change to the evaluation in one
 * belongs in the other as well; the compliance tests run both.
 *
 * <p>An evaluator runs one query once. Absolute queries inside filters ({@code $[?@.a == $.b]}) do not
 * depend on the current node, so each is evaluated once per run and its result reused; otherwise
 * nesting them would multiply the work by the document size per level. Every node visit counts
 * against {@link EvaluationLimits#maxVisitedNodes()}.
 */
public final class ValuesEvaluator {

    /** The thread's interrupt status is checked whenever the visit count passes a multiple of 2^12 = 4,096. */
    private static final int INTERRUPT_CHECK_SHIFT = 12;

    /**
     * Thrown instead of a failure at a node, whose location the evaluator does not know; the caller runs the query
     * again with an {@link Evaluator}, which throws the failure with the node's path. Without a stack trace, as it
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
    private final @Nullable Object root;
    private final long maxVisitedNodes;
    private final int maxResultSize;
    private final int maxDepth;
    private long visitedNodes;

    /** Results of absolute queries inside filters, created on first use. */
    private @Nullable IdentityHashMap<Query, List<@Nullable Object>> absoluteNodes;

    private @Nullable IdentityHashMap<Query, List<Val>> absoluteValues;

    @SuppressWarnings("unchecked")
    public ValuesEvaluator(@Nullable Object document, JsonModel<?> model, EvaluationLimits limits) {
        this.model = (JsonModel<@Nullable Object>) model;
        this.root = document;
        this.maxVisitedNodes = limits.maxVisitedNodes();
        this.maxResultSize = limits.maxResultSize();
        this.maxDepth = limits.maxDepth();
    }

    /** Receives the nodes a query selects, in order; returns false to stop the evaluation. */
    private interface Sink {
        boolean accept(@Nullable Object node);
    }

    /** Runs the query and returns the nodes it selects. */
    public List<@Nullable Object> run(Query query) {
        List<@Nullable Object> result = new ArrayList<>();
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

    private void checkLimits(long visited) {
        if (visited > maxVisitedNodes) {
            throw new JsonPathLimitExceededException("Query visited more than " + maxVisitedNodes + " nodes");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new JsonPathEvaluationException("Query evaluation was interrupted");
        }
    }

    /** Runs a query inside a filter; absolute queries are evaluated once per run. */
    private List<@Nullable Object> query(Query query, @Nullable Object current, int depth) {
        if (!query.absolute()) {
            List<@Nullable Object> selected = new ArrayList<>();
            evaluate(query, current, depth, selected::add);
            return selected;
        }
        if (absoluteNodes == null) {
            absoluteNodes = new IdentityHashMap<>();
        }
        List<@Nullable Object> selected = absoluteNodes.get(query);
        if (selected == null) {
            List<@Nullable Object> evaluated = new ArrayList<>();
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
    private @Nullable Object firstOf(Query query, @Nullable Object current, int depth) {
        if (query.absolute()) {
            List<@Nullable Object> selected = query(query, current, depth);
            return selected.isEmpty() ? None.NONE : selected.get(0);
        }
        return firstFrom(query, current, depth);
    }

    /** Evaluates a query from a start node until it selects the first node; returns it or {@link None#NONE}. */
    private @Nullable Object firstFrom(Query query, @Nullable Object start, int depth) {
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
    private void evaluate(Query query, @Nullable Object start, int depth, Sink sink) {
        List<Segment> segments = query.segments();
        apply(segments, 0, start, depth, sink);
    }

    /** Applies the segments from {@code index} on to one node; returns false if the sink stopped. */
    private boolean apply(List<Segment> segments, int index, @Nullable Object node, int depth, Sink sink) {
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
            @Nullable Object current = stack.pop();
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
            @Nullable Object node,
            int depth,
            @Nullable Object @Nullable [] children,
            int from,
            int to,
            List<Segment> segments,
            int next,
            Sink sink) {
        for (Selector selector : segment.selectors()) {
            if (!select(selector, node, depth, children, from, to, segments, next, sink)) {
                return false;
            }
        }
        return true;
    }

    /** Pushes the children of a node, in order, for the walk of a descendant segment. */
    private void pushChildren(@Nullable Object node, int depth, NodeStack stack) {
        try {
            Object value = node;
            JsonKind kind = model.kind(value);
            if (kind == JsonKind.OBJECT) {
                checkDepth(depth);
                visit(model.memberCount(value));
                for (Map.Entry<String, Object> member : model.members(value)) {
                    stack.push(member.getValue());
                }
            } else if (kind == JsonKind.ARRAY) {
                checkDepth(depth);
                int size = model.size(value);
                visit(size);
                for (int i = 0; i < size; i++) {
                    stack.push(model.element(value, i));
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
            throw Evaluator.tooDeep(maxDepth);
        }
    }

    /**
     * Replaces an exception from the model or a function, typically a value that is not JSON, with
     * {@link LocationNeeded}, since the evaluator does not know the location of the node.
     */
    @SuppressWarnings("unused") // the node, for symmetry with Evaluator
    private static JsonPathEvaluationException modelFailure(RuntimeException e, @Nullable Object node) {
        return new LocationNeeded();
    }

    private boolean select(
            Selector selector,
            @Nullable Object node,
            int depth,
            @Nullable Object @Nullable [] children,
            int from,
            int to,
            List<Segment> segments,
            int next,
            Sink sink) {
        if (selector instanceof Wildcard || selector instanceof Filter) {
            Expr expr = selector instanceof Filter filter ? filter.expr() : null;
            if (children == null) {
                return selectChildren(node, depth, expr, segments, next, sink);
            }
            // Produced again by this selector: counted like a second visit, as when computed again.
            visit(to - from);
            for (int i = from; i < to; i++) {
                if (!selectChild(children[i], depth + 1, expr, segments, next, sink)) {
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
        @Nullable Object selected;
        try {
            Object value = node;
            if (model.kind(value) != JsonKind.OBJECT) {
                return true;
            }
            String name = nameSelector.name();
            Object member = model.findMember(value, name);
            if (member == null && !model.hasMember(value, name)) {
                return true;
            }
            visit(1);
            selected = member;
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
        return apply(segments, next, selected, depth + 1, sink);
    }

    /** Index selector: passes the element at the index on, if the node is an array that has it. */
    private boolean selectIndex(
            Index selector, @Nullable Object node, int depth, List<Segment> segments, int next, Sink sink) {
        @Nullable Object selected;
        try {
            Object value = node;
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
            selected = model.element(value, i);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
        return apply(segments, next, selected, depth + 1, sink);
    }

    /** Slice selector: passes the selected elements on, if the node is an array. */
    private boolean selectSlice(
            Slice selector, @Nullable Object node, int depth, List<Segment> segments, int next, Sink sink) {
        long length;
        try {
            Object value = node;
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
            @Nullable Object node, int depth, @Nullable Expr filter, List<Segment> segments, int next, Sink sink) {
        Object value = node;
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
            @Nullable Object child, int depth, @Nullable Expr filter, List<Segment> segments, int next, Sink sink) {
        return (filter != null && !matches(filter, child, depth)) || apply(segments, next, child, depth, sink);
    }

    /** Whether an object node has another member. */
    private boolean hasNext(@Nullable Object node, Iterator<Map.Entry<String, @Nullable Object>> members) {
        try {
            return members.hasNext();
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /** The next member of an object node, which has one. */
    private @Nullable Object nextMember(@Nullable Object node, Iterator<Map.Entry<String, @Nullable Object>> members) {
        try {
            Map.Entry<String, @Nullable Object> member = members.next();
            return member.getValue();
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /** The element of an array node at an index that exists. */
    private @Nullable Object element(@Nullable Object node, int index) {
        try {
            return model.element(node, index);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /** Tests a filter expression on a candidate node; failures carry the candidate's location. */
    private boolean matches(Expr expr, @Nullable Object child, int depth) {
        try {
            return test(expr, child, depth);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, child);
        }
    }

    /** Array slice (RFC 9535, section 2.3.4.2): passes the selected elements of an array of length {@code len} on. */
    private boolean slice(
            Slice slice, @Nullable Object node, int depth, long len, List<Segment> segments, int next, Sink sink) {
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

    private boolean sliced(@Nullable Object node, int depth, int index, List<Segment> segments, int next, Sink sink) {
        visit(1);
        return apply(segments, next, element(node, index), depth + 1, sink);
    }

    // ---- logical expressions ----

    private boolean test(Expr expr, @Nullable Object current, int depth) {
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
    private boolean logical(Operand operand, @Nullable Object current, int depth) {
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

    private Val value(Operand operand, @Nullable Object current, int depth) {
        if (operand instanceof Literal literal) {
            return literal.val();
        }
        if (operand instanceof QueryOperand query) {
            Object first = firstOf(query.query(), current, depth);
            return first instanceof None ? Val.NOTHING : Val.of(first, model);
        }
        FunctionCall call = (FunctionCall) operand;
        FunctionDefinition function = call.function();
        return Val.from(
                ((ValueFunction) function.implementation()).body().apply(arguments(call, current, depth)),
                function.name());
    }

    private List<Val> nodes(Operand operand, @Nullable Object current, int depth) {
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

    private List<Val> values(List<@Nullable Object> selected) {
        List<Val> values = new ArrayList<>(selected.size());
        for (@Nullable Object node : selected) {
            values.add(Val.of(node, model));
        }
        return values;
    }

    /** Evaluates the arguments of a call as the declared parameter types require (RFC 9535, section 2.4.3). */
    private Arguments arguments(FunctionCall call, @Nullable Object current, int depth) {
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
