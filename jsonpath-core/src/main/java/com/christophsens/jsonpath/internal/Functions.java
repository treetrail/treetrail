package com.christophsens.jsonpath.internal;

import com.christophsens.jsonpath.JsonKind;
import com.christophsens.jsonpath.internal.FunctionDefinition.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The function extensions defined by RFC 9535, section 2.4.4 to 2.4.8.
 */
public final class Functions {

    public static final Map<String, FunctionDefinition> BUILT_IN = Map.of(
            "length", new FunctionDefinition("length", List.of(Type.VALUE), Type.VALUE, Functions::length),
            "count", new FunctionDefinition("count", List.of(Type.NODES), Type.VALUE, Functions::count),
            "match", new FunctionDefinition("match", List.of(Type.VALUE, Type.VALUE), Type.LOGICAL,
                    args -> regex(args, true)),
            "search", new FunctionDefinition("search", List.of(Type.VALUE, Type.VALUE), Type.LOGICAL,
                    args -> regex(args, false)),
            "value", new FunctionDefinition("value", List.of(Type.NODES), Type.VALUE, Functions::value));

    private Functions() {
    }

    private static Object length(List<Object> args) {
        Val v = (Val) args.get(0);
        if (v.isNothing()) {
            return Val.NOTHING;
        }
        switch (v.kind()) {
            case STRING:
                String s = v.string();
                return Val.literal(BigDecimal.valueOf(s.codePointCount(0, s.length())));
            case ARRAY:
                return Val.literal(BigDecimal.valueOf(v.model().size(v.value())));
            case OBJECT:
                return Val.literal(BigDecimal.valueOf(v.model().memberCount(v.value())));
            default:
                return Val.NOTHING;
        }
    }

    private static Object count(List<Object> args) {
        return Val.literal(BigDecimal.valueOf(((List<?>) args.get(0)).size()));
    }

    private static Object value(List<Object> args) {
        List<?> nodes = (List<?>) args.get(0);
        if (nodes.size() != 1) {
            return Val.NOTHING;
        }
        return nodes.get(0);
    }

    private static Object regex(List<Object> args, boolean fullMatch) {
        Val subject = (Val) args.get(0);
        Val regexp = (Val) args.get(1);
        if (subject.isNothing() || regexp.isNothing()
                || subject.kind() != JsonKind.STRING
                || regexp.kind() != JsonKind.STRING) {
            return false;
        }
        Optional<Pattern> pattern = IRegexp.compile(regexp.string());
        if (pattern.isEmpty()) {
            return false;
        }
        var matcher = pattern.get().matcher(subject.string());
        return fullMatch ? matcher.matches() : matcher.find();
    }
}
