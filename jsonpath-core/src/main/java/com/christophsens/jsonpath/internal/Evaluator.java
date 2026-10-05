package com.christophsens.jsonpath.internal;

import com.christophsens.jsonpath.JsonKind;
import com.christophsens.jsonpath.JsonModel;
import com.christophsens.jsonpath.internal.Ast.And;
import com.christophsens.jsonpath.internal.Ast.Argument;
import com.christophsens.jsonpath.internal.Ast.Comparison;
import com.christophsens.jsonpath.internal.Ast.Expr;
import com.christophsens.jsonpath.internal.Ast.Filter;
import com.christophsens.jsonpath.internal.Ast.FunctionCall;
import com.christophsens.jsonpath.internal.Ast.Index;
import com.christophsens.jsonpath.internal.Ast.Literal;
import com.christophsens.jsonpath.internal.Ast.Name;
import com.christophsens.jsonpath.internal.Ast.Not;
import com.christophsens.jsonpath.internal.Ast.Operand;
import com.christophsens.jsonpath.internal.Ast.Or;
import com.christophsens.jsonpath.internal.Ast.Paren;
import com.christophsens.jsonpath.internal.Ast.Query;
import com.christophsens.jsonpath.internal.Ast.QueryOperand;
import com.christophsens.jsonpath.internal.Ast.Segment;
import com.christophsens.jsonpath.internal.Ast.Selector;
import com.christophsens.jsonpath.internal.Ast.Slice;
import com.christophsens.jsonpath.internal.Ast.Test;
import com.christophsens.jsonpath.internal.Ast.Wildcard;
import com.christophsens.jsonpath.internal.FunctionDefinition.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Executes a parsed query against a document (RFC 9535, sections 2.3 to 2.5).
 */
public final class Evaluator {

    private final JsonModel<Object> model;
    private final Located root;

    @SuppressWarnings("unchecked")
    public Evaluator(Object document, JsonModel<?> model) {
        this.model = (JsonModel<Object>) model;
        this.root = new Located(document, Location.ROOT);
    }

    public List<Located> run(Query query) {
        return query(query, root);
    }

    private List<Located> query(Query query, Located current) {
        List<Located> nodes = List.of(query.absolute() ? root : current);
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
        Object value = node.value();
        JsonKind kind = model.kind(value);
        if (kind == JsonKind.OBJECT) {
            List<Located> children = new ArrayList<>(model.memberCount(value));
            for (String name : model.memberNames(value)) {
                children.add(new Located(model.member(value, name), node.location().child(name)));
            }
            return children;
        }
        if (kind == JsonKind.ARRAY) {
            int size = model.size(value);
            List<Located> children = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                children.add(new Located(model.element(value, i), node.location().child(i)));
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
        Object value = node.value();
        JsonKind kind = model.kind(value);
        if (selector instanceof Name) {
            String name = ((Name) selector).name();
            if (kind == JsonKind.OBJECT && model.hasMember(value, name)) {
                out.add(new Located(model.member(value, name), node.location().child(name)));
            }
        } else if (selector instanceof Wildcard) {
            out.addAll(children(node));
        } else if (selector instanceof Index) {
            if (kind == JsonKind.ARRAY) {
                int size = model.size(value);
                long index = ((Index) selector).index();
                long normalized = index >= 0 ? index : size + index;
                if (normalized >= 0 && normalized < size) {
                    int i = (int) normalized;
                    out.add(new Located(model.element(value, i), node.location().child(i)));
                }
            }
        } else if (selector instanceof Slice) {
            if (kind == JsonKind.ARRAY) {
                slice((Slice) selector, node, out);
            }
        } else if (selector instanceof Filter) {
            Expr expr = ((Filter) selector).expr();
            for (Located child : children(node)) {
                if (test(expr, child)) {
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

    private void add(Object array, int index, Located parent, List<Located> out) {
        out.add(new Located(model.element(array, index), parent.location().child(index)));
    }

    // ---- logical expressions ----

    private boolean test(Expr expr, Located current) {
        if (expr instanceof Or) {
            for (Expr operand : ((Or) expr).operands()) {
                if (test(operand, current)) {
                    return true;
                }
            }
            return false;
        }
        if (expr instanceof And) {
            for (Expr operand : ((And) expr).operands()) {
                if (!test(operand, current)) {
                    return false;
                }
            }
            return true;
        }
        if (expr instanceof Not) {
            return !test(((Not) expr).operand(), current);
        }
        if (expr instanceof Paren) {
            return test(((Paren) expr).operand(), current);
        }
        if (expr instanceof Comparison) {
            Comparison comparison = (Comparison) expr;
            return Values.compare(value(comparison.left(), current), comparison.op(),
                    value(comparison.right(), current));
        }
        Operand operand = ((Test) expr).operand();
        return logical(operand, current);
    }

    /** Converts a query or function result to a logical value (NodesType: non-empty). */
    private boolean logical(Operand operand, Located current) {
        if (operand instanceof QueryOperand) {
            return !query(((QueryOperand) operand).query(), current).isEmpty();
        }
        FunctionCall call = (FunctionCall) operand;
        Object result = call(call, current);
        if (call.function().result() == Type.NODES) {
            return !((List<?>) result).isEmpty();
        }
        return (Boolean) result;
    }

    private Val value(Operand operand, Located current) {
        if (operand instanceof Literal) {
            return Val.literal(((Literal) operand).value());
        }
        if (operand instanceof QueryOperand) {
            List<Located> nodes = query(((QueryOperand) operand).query(), current);
            return nodes.isEmpty() ? Val.NOTHING : Val.of(nodes.get(0).value(), model);
        }
        return (Val) call((FunctionCall) operand, current);
    }

    private List<Val> nodes(Operand operand, Located current) {
        if (operand instanceof QueryOperand) {
            List<Located> nodes = query(((QueryOperand) operand).query(), current);
            List<Val> values = new ArrayList<>(nodes.size());
            for (Located node : nodes) {
                values.add(Val.of(node.value(), model));
            }
            return values;
        }
        @SuppressWarnings("unchecked")
        List<Val> result = (List<Val>) call((FunctionCall) operand, current);
        return result;
    }

    private Object call(FunctionCall call, Located current) {
        List<Type> parameters = call.function().parameters();
        List<Object> args = new ArrayList<>(parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            Argument argument = call.arguments().get(i);
            switch (parameters.get(i)) {
                case VALUE:
                    args.add(value((Operand) argument, current));
                    break;
                case LOGICAL:
                    args.add(argument instanceof Expr
                            ? test((Expr) argument, current)
                            : logical((Operand) argument, current));
                    break;
                case NODES:
                    args.add(nodes((Operand) argument, current));
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
        return call.function().body().apply(args);
    }
}
