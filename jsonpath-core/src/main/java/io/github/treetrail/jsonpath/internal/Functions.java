package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.FunctionArguments;
import io.github.treetrail.jsonpath.FunctionType;
import io.github.treetrail.jsonpath.FunctionValue;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.internal.Ast.FunctionCall;
import io.github.treetrail.jsonpath.internal.Ast.Literal;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.LogicalFunction;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.ValueFunction;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The function extensions defined by RFC 9535, section 2.4.4 to 2.4.8.
 */
public final class Functions {

    private static final FunctionType VALUE = FunctionType.VALUE;
    private static final FunctionType NODES = FunctionType.NODES;

    private static final FunctionDefinition MATCH =
            new FunctionDefinition("match", List.of(VALUE, VALUE), new LogicalFunction(args -> regex(args, true)));
    private static final FunctionDefinition SEARCH =
            new FunctionDefinition("search", List.of(VALUE, VALUE), new LogicalFunction(args -> regex(args, false)));

    /** The functions of RFC 9535, by name; written against the same types as function extensions. */
    public static final Map<String, FunctionDefinition> BUILT_IN = Map.of(
            "length", new FunctionDefinition("length", List.of(VALUE), new ValueFunction(Functions::length)),
            "count", new FunctionDefinition("count", List.of(NODES), new ValueFunction(Functions::count)),
            "match", MATCH,
            "search", SEARCH,
            "value", new FunctionDefinition("value", List.of(NODES), new ValueFunction(Functions::value)));

    private Functions() {}

    private static FunctionValue length(FunctionArguments args) {
        FunctionValue v = args.value(0);
        if (v.isNothing()) {
            return FunctionValue.nothing();
        }
        return switch (v.kind()) {
            case STRING -> {
                String s = v.string();
                yield FunctionValue.of(s.codePointCount(0, s.length()));
            }
            case ARRAY, OBJECT -> FunctionValue.of(v.size());
            default -> FunctionValue.nothing();
        };
    }

    private static FunctionValue count(FunctionArguments args) {
        return FunctionValue.of(args.nodes(0).size());
    }

    private static FunctionValue value(FunctionArguments args) {
        List<FunctionValue> nodes = args.nodes(0);
        return nodes.size() == 1 ? nodes.get(0) : FunctionValue.nothing();
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
            FunctionValue subject = args.value(0);
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

    private static boolean regex(FunctionArguments args, boolean fullMatch) {
        FunctionValue subject = args.value(0);
        FunctionValue regexp = args.value(1);
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
