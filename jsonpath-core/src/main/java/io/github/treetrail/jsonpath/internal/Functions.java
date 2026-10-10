package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.JsonKind;
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

    public static final Map<String, FunctionDefinition> BUILT_IN = Map.of(
            "length", new FunctionDefinition("length", List.of(VALUE), new ValueFunction(Functions::length)),
            "count", new FunctionDefinition("count", List.of(NODES), new ValueFunction(Functions::count)),
            "match",
                    new FunctionDefinition(
                            "match", List.of(VALUE, VALUE), new LogicalFunction(args -> regex(args, true))),
            "search",
                    new FunctionDefinition(
                            "search", List.of(VALUE, VALUE), new LogicalFunction(args -> regex(args, false))),
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
