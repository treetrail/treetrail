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

    public List<Located> run(Query query) {
        List<Located> result = evaluate(query, root);
        if (result.size() > maxResultSize) {
            throw new JsonPathLimitExceededException(
                    "Query selected " + result.size() + " nodes, more than the limit of " + maxResultSize);
        }
        return result;
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
            return evaluate(query, current);
        }
        if (absoluteNodes == null) {
            absoluteNodes = new IdentityHashMap<>();
        }
        List<Located> nodes = absoluteNodes.get(query);
        if (nodes == null) {
            nodes = evaluate(query, root);
            absoluteNodes.put(query, nodes);
        }
        return nodes;
    }

    private List<Located> evaluate(Query query, Located current) {
        List<Located> nodes = List.of(current);
        for (Segment segment : query.segments()) {
            List<Located> out = new ArrayList<>();
            for (Located node : nodes) {
                if (segment.descendant()) {
                    for (Located descendant : descendantsAndSelf(node)) {
                        select(segment, descendant, out);
                    }
                } else {
                    select(segment, node, out);
                }
            }
            nodes = out;
        }
        return nodes;
    }

    /** The node and all its descendants, each node before its children, children in order. */
    private List<Located> descendantsAndSelf(Located start) {
        List<Located> result = new ArrayList<>();
        Deque<Located> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            Located node = stack.pop();
            result.add(node);
            List<Located> children = children(node);
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return result;
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

    private void select(Segment segment, Located node, List<Located> out) {
        for (Selector selector : segment.selectors()) {
            select(selector, node, out);
        }
    }

    private void select(Selector selector, Located node, List<Located> out) {
        try {
            selectFrom(selector, node, out);
        } catch (JsonPathEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw modelFailure(e, node);
        }
    }

    /**
     * Wraps an exception from the model or a function, typically a value that is not JSON, with the
     * location of the node being processed. Exceptions of nested queries carry their own, closer node.
     */
    private static JsonPathEvaluationException modelFailure(RuntimeException e, Located node) {
        String reason = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
        return new JsonPathEvaluationException(reason, node.location().normalizedPath(), e);
    }

    private void selectFrom(Selector selector, Located node, List<Located> out) {
        Object value = node.value();
        JsonKind kind = model.kind(value);
        if (selector instanceof Name nameSelector) {
            String name = nameSelector.name();
            if (kind == JsonKind.OBJECT) {
                Object member = model.findMember(value, name);
                if (member != null || model.hasMember(value, name)) {
                    visit(1);
                    out.add(new Located(member, node.location().child(name)));
                }
            }
        } else if (selector instanceof Wildcard) {
            out.addAll(children(node));
        } else if (selector instanceof Index indexSelector) {
            if (kind == JsonKind.ARRAY) {
                int size = model.size(value);
                long index = indexSelector.index();
                long normalized = index >= 0 ? index : size + index;
                if (normalized >= 0 && normalized < size) {
                    int i = (int) normalized;
                    visit(1);
                    out.add(new Located(model.element(value, i), node.location().child(i)));
                }
            }
        } else if (selector instanceof Slice slice) {
            if (kind == JsonKind.ARRAY) {
                slice(slice, node, out);
            }
        } else if (selector instanceof Filter filter) {
            Expr expr = filter.expr();
            for (Located child : children(node)) {
                boolean selected;
                try {
                    selected = test(expr, child);
                } catch (JsonPathEvaluationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw modelFailure(e, child);
                }
                if (selected) {
                    out.add(child);
                }
            }
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
            return !query(query.query(), current).isEmpty();
        }
        FunctionCall call = (FunctionCall) operand;
        Object result = call(call, current);
        if (call.function().result() == FunctionDefinition.Type.NODES) {
            return !((List<?>) result).isEmpty();
        }
        return (Boolean) result;
    }

    private Val value(Operand operand, Located current) {
        if (operand instanceof Literal literal) {
            return Val.literal(literal.value());
        }
        if (operand instanceof QueryOperand query) {
            List<Located> nodes = query(query.query(), current);
            return nodes.isEmpty() ? Val.NOTHING : Val.of(nodes.get(0).value(), model);
        }
        return (Val) call((FunctionCall) operand, current);
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
        @SuppressWarnings("unchecked")
        List<Val> result = (List<Val>) call((FunctionCall) operand, current);
        return result;
    }

    private List<Val> values(List<Located> nodes) {
        List<Val> values = new ArrayList<>(nodes.size());
        for (Located node : nodes) {
            values.add(Val.of(node.value(), model));
        }
        return values;
    }

    private Object call(FunctionCall call, Located current) {
        List<FunctionDefinition.Type> parameters = call.function().parameters();
        List<Object> args = new ArrayList<>(parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            Argument argument = call.arguments().get(i);
            switch (parameters.get(i)) {
                case VALUE -> args.add(value((Operand) argument, current));
                case LOGICAL ->
                    args.add(
                            argument instanceof Expr expr ? test(expr, current) : logical((Operand) argument, current));
                case NODES -> args.add(nodes((Operand) argument, current));
            }
        }
        return call.function().body().apply(args);
    }
}
