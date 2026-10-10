package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.internal.Ast.FunctionCall;
import io.github.treetrail.jsonpath.internal.Ast.Literal;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.Arguments;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.LogicalFunction;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.ValueFunction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The function extensions defined by RFC 9535, section 2.4.4 to 2.4.8.
 */
public final class Functions {

    private static final FunctionDefinition.Type VALUE = FunctionDefinition.Type.VALUE;
    private static final FunctionDefinition.Type NODES = FunctionDefinition.Type.NODES;

    private static final FunctionDefinition MATCH =
            new FunctionDefinition("match", List.of(VALUE, VALUE), new LogicalFunction(args -> regex(args, true)));
    private static final FunctionDefinition SEARCH =
            new FunctionDefinition("search", List.of(VALUE, VALUE), new LogicalFunction(args -> regex(args, false)));

    public static final Map<String, FunctionDefinition> BUILT_IN = Map.of(
            "length", new FunctionDefinition("length", List.of(VALUE), new ValueFunction(Functions::length)),
            "count", new FunctionDefinition("count", List.of(NODES), new ValueFunction(Functions::count)),
            "match", MATCH,
            "search", SEARCH,
            "value", new FunctionDefinition("value", List.of(NODES), new ValueFunction(Functions::value)));

    private Functions() {}

    private static Val length(Arguments args) {
        Val v = args.value(0);
        if (v.isNothing()) {
            return Val.NOTHING;
        }
        switch (v.kind()) {
            case STRING -> {
                String s = v.string();
                return Val.literal(BigDecimal.valueOf(s.codePointCount(0, s.length())));
            }
            case ARRAY -> {
                return Val.literal(BigDecimal.valueOf(v.model().size(v.value())));
            }
            case OBJECT -> {
                return Val.literal(BigDecimal.valueOf(v.model().memberCount(v.value())));
            }
            default -> {
                return Val.NOTHING;
            }
        }
    }

    private static Val count(Arguments args) {
        return Val.literal(BigDecimal.valueOf(args.nodes(0).size()));
    }

    private static Val value(Arguments args) {
        List<Val> nodes = args.nodes(0);
        if (nodes.size() != 1) {
            return Val.NOTHING;
        }
        return nodes.get(0);
    }

    /**
     * Returns the call itself, or for {@code match()} and {@code search()} with a literal pattern a call
     * whose pattern is compiled once, here, instead of being looked up for every node.
     */
    public static FunctionCall specialize(FunctionCall call) {
        FunctionDefinition function = call.function();
        boolean fullMatch = function.equals(MATCH);
        if ((!fullMatch && !function.equals(SEARCH))
                || !(call.arguments().get(1) instanceof Literal literal)
                || !(literal.value() instanceof String pattern)) {
            return call;
        }
        Optional<IRegexp> compiled = IRegexp.compileForQuery(pattern);
        LogicalFunction precompiled = new LogicalFunction(args -> {
            Val subject = args.value(0);
            if (compiled.isEmpty() || subject.isNothing() || subject.kind() != JsonKind.STRING) {
                return false;
            }
            return fullMatch
                    ? compiled.get().matches(subject.string())
                    : compiled.get().find(subject.string());
        });
        return new FunctionCall(
                new FunctionDefinition(function.name(), function.parameters(), precompiled), call.arguments());
    }

    private static boolean regex(Arguments args, boolean fullMatch) {
        Val subject = args.value(0);
        Val regexp = args.value(1);
        if (subject.isNothing()
                || regexp.isNothing()
                || subject.kind() != JsonKind.STRING
                || regexp.kind() != JsonKind.STRING) {
            return false;
        }
        Optional<IRegexp> compiled = IRegexp.compile(regexp.string());
        if (compiled.isEmpty()) {
            return false;
        }
        return fullMatch
                ? compiled.get().matches(subject.string())
                : compiled.get().find(subject.string());
    }
}
