package io.github.treetrail.jsonpath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Generators for JSON documents as plain Java objects, for property tests.
 *
 * <p>Member names come from a small set of awkward names (quotes, backslashes, control characters,
 * non-ASCII and supplementary characters), so that names repeat across a document and normalized paths
 * need every kind of escape. Numbers come as different Java types that are equal by value.
 */
final class JsonArbitraries {

    /** Names that are hard to write in a normalized path; the first few are valid shorthand names. */
    static final List<String> NAMES = List.of(
            "a",
            "b",
            "c",
            "_x",
            "é",
            "",
            " ",
            "it's",
            "\"quoted\"",
            "back\\slash",
            "tab\there",
            "nl\n",
            "\u0001",
            "\u001f",
            "😀",
            "[0]",
            "$",
            "@",
            "a.b",
            "*");

    private JsonArbitraries() {}

    /** Non-empty objects and arrays, nested up to four levels: on average about 18 nodes. */
    static Arbitrary<Object> documents() {
        return container(4, 1);
    }

    /** Any JSON value, including scalars and empty containers. */
    static Arbitrary<Object> values() {
        return value(3);
    }

    static Arbitrary<Object> scalars() {
        return Arbitraries.oneOf(Arbitraries.just(null), Arbitraries.of(true, false), numbers(), strings());
    }

    static Arbitrary<Object> numbers() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(-3, 3).map(i -> i),
                Arbitraries.longs().between(-3, 3).map(l -> l),
                Arbitraries.integers().between(-3, 3).map(i -> (double) i),
                Arbitraries.integers().between(-300, 300).map(i -> new BigDecimal(i).movePointLeft(2)),
                Arbitraries.of(0.5, -2.25, 1e10, 9007199254740991L, -0.0));
    }

    static Arbitrary<Object> strings() {
        return Arbitraries.oneOf(
                Arbitraries.of(NAMES).map(s -> s),
                Arbitraries.strings().withCharRange('a', 'e').ofMaxLength(3).map(s -> s));
    }

    private static Arbitrary<Object> value(int depth) {
        if (depth == 0) {
            return scalars();
        }
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(2, scalars()), net.jqwik.api.Tuple.of(3, container(depth, 0)));
    }

    private static Arbitrary<Object> container(int depth, int minSize) {
        Arbitrary<Object> element = Arbitraries.lazy(() -> value(depth - 1));
        Arbitrary<Object> array = element.list().ofMinSize(minSize).ofMaxSize(5).map(list -> new ArrayList<>(list));
        Arbitrary<Object> object = Arbitraries.maps(Arbitraries.of(NAMES), element)
                .ofMinSize(minSize)
                .ofMaxSize(5)
                .map(map -> (Object) new LinkedHashMap<>(map));
        return Arbitraries.oneOf(array, object);
    }

    /** A document with one object holding two values, for comparing them through queries. */
    static Object pair(Object left, Object right) {
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("l", left);
        holder.put("r", right);
        List<Object> document = new ArrayList<>();
        document.add(holder);
        return document;
    }
}
