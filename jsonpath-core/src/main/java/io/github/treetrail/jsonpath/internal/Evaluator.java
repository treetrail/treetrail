package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.EvaluationLimits;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Executes a parsed query against a document (RFC 9535, sections 2.3 to 2.5).
 *
 * <p>An evaluator runs one query once. Absolute queries inside filters ({@code $[?@.a == $.b]}) do not
 * depend on the current node, so each is evaluated once per run and its result reused; otherwise
 * nesting them would multiply the work by the document size per level. Every node visit counts
 * against {@link EvaluationLimits#maxVisitedNodes()}.
 */
public final class Evaluator {

    /** The thread's interrupt status is checked whenever the visit count passes a multiple of 2^12 = 4,096. */
    private static final int INTERRUPT_CHECK_SHIFT = 12;

    private final JsonModel<@Nullable Object> model;
    private final Located root;
    private final long maxVisitedNodes;
    private final int maxResultSize;
    private final int maxDepth;
    private long visitedNodes;

    /** Results of absolute queries inside filters, created on first use. */
    private @Nullable IdentityHashMap<Query, List<Located>> absoluteNodes;

    private @Nullable IdentityHashMap<Query, List<Val>> absoluteValues;

    @SuppressWarnings("unchecked")
    public Evaluator(Object document, JsonModel<?> model, EvaluationLimits limits) {
        this.model = (JsonModel<@Nullable Object>) model;
        this.root = new Located(document, Location.ROOT);
        this.maxVisitedNodes = limits.maxVisitedNodes();
        this.maxResultSize = limits.maxResultSize();
        this.maxDepth = limits.maxDepth();
    }

    /** Receives the nodes a query selects, in order; returns false to stop the evaluation. */
    private interface Sink {
        boolean accept(Located node);
    }

    /** Runs the query and returns the nodes it selects. */
    public List<Located> run(Query query) {
        List<Located> result = new ArrayList<>();
        evaluate(query, root, node -> {
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
        evaluate(query, root, node -> {
            found[0] = true;
            return false;
        });
        return found[0];
    }

    /** Returns the first node the query selects, or null; stops there. */
    public @Nullable Located first(Query query) {
        return firstFrom(query, root);
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
    private List<Located> query(Query query, Located current) {
        if (!query.absolute()) {
            List<Located> nodes = new ArrayList<>();
            evaluate(query, current, nodes::add);
            return nodes;
        }
        if (absoluteNodes == null) {
            absoluteNodes = new IdentityHashMap<>();
        }
        List<Located> nodes = absoluteNodes.get(query);
        if (nodes == null) {
            List<Located> evaluated = new ArrayList<>();
            evaluate(query, root, evaluated::add);
            nodes = evaluated;
            absoluteNodes.put(query, nodes);
        }
        return nodes;
    }

    /** The first node a query inside a filter selects, or null; absolute queries use their result of this run. */
    private @Nullable Located firstOf(Query query, Located current) {
        if (query.absolute()) {
            List<Located> nodes = query(query, current);
            return nodes.isEmpty() ? null : nodes.get(0);
        }
        return firstFrom(query, current);
    }

    /** Evaluates a query from a start node until it selects the first node. */
    private @Nullable Located firstFrom(Query query, Located start) {
        Located[] first = {null};
        evaluate(query, start, node -> {
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
    private void evaluate(Query query, Located start, Sink sink) {
        List<Segment> segments = query.segments();
        apply(segments, 0, start, sink);
    }

    /** Applies the segments from {@code index} on to one node; returns false if the sink stopped. */
    private boolean apply(List<Segment> segments, int index, Located node, Sink sink) {
        if (index == segments.size()) {
            return sink.accept(node);
        }
        Segment segment = segments.get(index);
        if (!segment.descendant()) {
            return select(segment, node, null, segments, index + 1, sink);
        }
        // Descendant segment: the node and all its descendants in document order (each node before its
        // children, children in order), each with the segment's selectors. The children of a node are
        // computed once, both for the walk and for wildcard and filter selectors.
        Deque<Located> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            Located current = stack.pop();
            List<Located> children = children(current);
            if (!select(segment, current, children, segments, index + 1, sink)) {
                return false;
            }
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return true;
    }

    /**
     * Applies a segment's selectors to a node and passes what they select on to the next segment.
     * {@code children} are the node's children if the caller has computed them already, else null.
     */
    private boolean select(
            Segment segment,
            Located node,
            @Nullable List<Located> children,
            List<Segment> segments,
            int next,
            Sink sink) {
        for (Selector selector : segment.selectors()) {
            if (!select(selector, node, children, segments, next, sink)) {
                return false;
            }
        }
        return true;
    }

    private List<Located> children(Located node) {
        try {
            return childrenOf(node);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    private List<Located> childrenOf(Located node) {
        Object value = node.value();
        JsonKind kind = model.kind(value);
        if ((kind == JsonKind.OBJECT || kind == JsonKind.ARRAY)
                && node.location().depth() >= maxDepth) {
            throw tooDeep(maxDepth);
        }
        if (kind == JsonKind.OBJECT) {
            int count = model.memberCount(value);
            visit(count);
            List<Located> children = new ArrayList<>(count);
            for (Map.Entry<String, Object> member : model.members(value)) {
                children.add(new Located(member.getValue(), node.location().child(member.getKey())));
            }
            return children;
        }
        if (kind == JsonKind.ARRAY) {
            int size = model.size(value);
            visit(size);
            List<Located> children = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                children.add(
                        new Located(model.element(value, i), node.location().child(i)));
            }
            return children;
        }
        return List.of();
    }

    /**
     * Wraps an exception from the model or a function, typically a value that is not JSON, with the
     * location of the node being processed. Exceptions of nested queries and of later segments carry
     * their own, closer node.
     */
    private static JsonPathEvaluationException modelFailure(RuntimeException e, Located node) {
        String reason = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
        return new JsonPathEvaluationException(reason, node.location().normalizedPath(), e);
    }

    private boolean select(
            Selector selector,
            Located node,
            @Nullable List<Located> children,
            List<Segment> segments,
            int next,
            Sink sink) {
        if (selector instanceof Wildcard || selector instanceof Filter) {
            List<Located> candidates;
            if (children == null) {
                candidates = children(node);
            } else {
                // Produced again by this selector: counted like a second visit, as when computed again.
                visit(children.size());
                candidates = children;
            }
            Expr expr = selector instanceof Filter filter ? filter.expr() : null;
            for (Located child : candidates) {
                if (expr != null && !matches(expr, child)) {
                    continue;
                }
                if (!apply(segments, next, child, sink)) {
                    return false;
                }
            }
            return true;
        }
        Located selected;
        List<Located> sliced = null;
        try {
            Object value = node.value();
            JsonKind kind = model.kind(value);
            selected = null;
            if (selector instanceof Name nameSelector) {
                if (kind == JsonKind.OBJECT) {
                    String name = nameSelector.name();
                    Object member = model.findMember(value, name);
                    if (member != null || model.hasMember(value, name)) {
                        visit(1);
                        selected = new Located(member, node.location().child(name));
                    }
                }
            } else if (selector instanceof Index indexSelector) {
                if (kind == JsonKind.ARRAY) {
                    int size = model.size(value);
                    long index = indexSelector.index();
                    long normalized = index >= 0 ? index : size + index;
                    if (normalized >= 0 && normalized < size) {
                        int i = (int) normalized;
                        visit(1);
                        selected = new Located(
                                model.element(value, i), node.location().child(i));
                    }
                }
            } else if (kind == JsonKind.ARRAY) {
                sliced = new ArrayList<>();
                slice((Slice) selector, node, sliced);
            }
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
        if (selected != null) {
            return apply(segments, next, selected, sink);
        }
        if (sliced != null) {
            for (Located element : sliced) {
                if (!apply(segments, next, element, sink)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Tests a filter expression on a candidate node; failures carry the candidate's location. */
    private boolean matches(Expr expr, Located child) {
        try {
            return test(expr, child);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, child);
        }
    }

    /** Array slice (RFC 9535, section 2.3.4.2). */
    private void slice(Slice slice, Located node, List<Located> out) {
        Object array = node.value();
        long len = model.size(array);
        long step = slice.step() == null ? 1 : slice.step();
        if (step == 0) {
            return;
        }
        long start = slice.start() != null ? slice.start() : (step >= 0 ? 0 : len - 1);
        long end = slice.end() != null ? slice.end() : (step >= 0 ? len : -len - 1);
        long nStart = start >= 0 ? start : len + start;
        long nEnd = end >= 0 ? end : len + end;
        if (step > 0) {
            long lower = Math.min(Math.max(nStart, 0), len);
            long upper = Math.min(Math.max(nEnd, 0), len);
            for (long i = lower; i < upper; i += step) {
                add(array, (int) i, node, out);
            }
        } else {
            long upper = Math.min(Math.max(nStart, -1), len - 1);
            long lower = Math.min(Math.max(nEnd, -1), len - 1);
            for (long i = upper; lower < i; i += step) {
                add(array, (int) i, node, out);
            }
        }
    }

    private void add(@Nullable Object array, int index, Located parent, List<Located> out) {
        visit(1);
        out.add(new Located(model.element(array, index), parent.location().child(index)));
    }

    // ---- logical expressions ----

    private boolean test(Expr expr, Located current) {
        if (expr instanceof Or or) {
            for (Expr operand : or.operands()) {
                if (test(operand, current)) {
                    return true;
                }
            }
            return false;
        }
        if (expr instanceof And and) {
            for (Expr operand : and.operands()) {
                if (!test(operand, current)) {
                    return false;
                }
            }
            return true;
        }
        if (expr instanceof Not not) {
            return !test(not.operand(), current);
        }
        if (expr instanceof Paren paren) {
            return test(paren.operand(), current);
        }
        if (expr instanceof Comparison comparison) {
            return Values.compare(
                    value(comparison.left(), current), comparison.op(), value(comparison.right(), current), maxDepth);
        }
        Operand operand = ((Test) expr).operand();
        return logical(operand, current);
    }

    /** Converts a query or function result to a logical value (NodesType: non-empty). */
    private boolean logical(Operand operand, Located current) {
        if (operand instanceof QueryOperand query) {
            return firstOf(query.query(), current) != null;
        }
        FunctionCall call = (FunctionCall) operand;
        FunctionDefinition.Implementation implementation = call.function().implementation();
        if (implementation instanceof LogicalFunction function) {
            return function.body().test(arguments(call, current));
        }
        return !((NodesFunction) implementation)
                .body()
                .apply(arguments(call, current))
                .isEmpty();
    }

    private Val value(Operand operand, Located current) {
        if (operand instanceof Literal literal) {
            return literal.val();
        }
        if (operand instanceof QueryOperand query) {
            Located first = firstOf(query.query(), current);
            return first == null ? Val.NOTHING : Val.of(first.value(), model);
        }
        FunctionCall call = (FunctionCall) operand;
        return ((ValueFunction) call.function().implementation()).body().apply(arguments(call, current));
    }

    private List<Val> nodes(Operand operand, Located current) {
        if (operand instanceof QueryOperand queryOperand) {
            Query query = queryOperand.query();
            if (!query.absolute()) {
                return values(query(query, current));
            }
            // Converted once as well, so that count($..*) in a filter costs O(1) per node.
            if (absoluteValues == null) {
                absoluteValues = new IdentityHashMap<>();
            }
            List<Val> values = absoluteValues.get(query);
            if (values == null) {
                values = Collections.unmodifiableList(values(query(query, current)));
                absoluteValues.put(query, values);
            }
            return values;
        }
        FunctionCall call = (FunctionCall) operand;
        return ((NodesFunction) call.function().implementation()).body().apply(arguments(call, current));
    }

    private List<Val> values(List<Located> nodes) {
        List<Val> values = new ArrayList<>(nodes.size());
        for (Located node : nodes) {
            values.add(Val.of(node.value(), model));
        }
        return values;
    }

    /** Evaluates the arguments of a call as the declared parameter types require (RFC 9535, section 2.4.3). */
    private Arguments arguments(FunctionCall call, Located current) {
        List<FunctionDefinition.Type> parameters = call.function().parameters();
        Object[] args = new Object[parameters.size()];
        for (int i = 0; i < args.length; i++) {
            Argument argument = call.arguments().get(i);
            args[i] = switch (parameters.get(i)) {
                case VALUE -> value((Operand) argument, current);
                case LOGICAL ->
                    argument instanceof Expr expr ? test(expr, current) : logical((Operand) argument, current);
                case NODES -> nodes((Operand) argument, current);
            };
        }
        return new Arguments(args);
    }
}
