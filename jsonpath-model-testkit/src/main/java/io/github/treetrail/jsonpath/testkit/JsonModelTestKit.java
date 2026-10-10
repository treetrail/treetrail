package io.github.treetrail.jsonpath.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import io.github.treetrail.jsonpath.NodeList;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;

/**
 * Tests for a {@link JsonModel} implementation, as JUnit dynamic tests. Hand over the model and a function
 * that parses JSON text with your library:
 *
 * <pre>{@code
 * class MyModelTest {
 *     @TestFactory
 *     Stream<DynamicTest> complianceSuite() {
 *         return JsonModelTestKit.complianceTests(MyModel.INSTANCE, MyLibrary::parse);
 *     }
 *
 *     @TestFactory
 *     Stream<DynamicTest> contract() {
 *         return JsonModelTestKit.contractTests(MyModel.INSTANCE, MyLibrary::parse);
 *     }
 * }
 * }</pre>
 *
 * <p>{@link #complianceTests} runs every case of the
 * <a href="https://github.com/jsonpath-standard/jsonpath-compliance-test-suite">JSONPath Compliance Test
 * Suite</a> (BSD-2 license, included in this jar) through the model: each document is handed to your parser
 * as text, so the tests cover your library's parsing as well. {@link #contractTests} checks the methods of
 * {@link JsonModel} one by one, which points to the cause faster when a compliance case fails.
 */
public final class JsonModelTestKit {

    private static final String CTS = "cts/cts.json";

    private JsonModelTestKit() {}

    /**
     * Returns one test per case of the JSONPath Compliance Test Suite: invalid selectors must be rejected,
     * valid ones must select the expected values (compared as JSON values) at the expected paths.
     *
     * @param model the model under test
     * @param parse parses JSON text into the model's node type
     */
    public static <N extends @Nullable Object> Stream<DynamicTest> complianceTests(
            JsonModel<N> model, Function<String, N> parse) {
        return complianceCases().stream()
                .map(test -> DynamicTest.dynamicTest(
                        test.name() + " | " + test.selector(), () -> runComplianceCase(test, model, parse)));
    }

    /** Returns the cases of the JSONPath Compliance Test Suite, in the order of the suite. */
    public static List<ComplianceCase> complianceCases() {
        Map<?, ?> suite = (Map<?, ?>) Objects.requireNonNull(JavaObjectModel.parse(readSuite()));
        List<ComplianceCase> cases = new ArrayList<>();
        for (Object test : (List<?>) Objects.requireNonNull(suite.get("tests"))) {
            cases.add(toCase((Map<?, ?>) test));
        }
        return List.copyOf(cases);
    }

    /**
     * Returns tests of the {@link JsonModel} contract: kinds of all JSON values, members (including the
     * difference between a missing member and a member that is JSON null), array elements, strings, numbers
     * and booleans, and equality with {@link JavaObjectModel} for every document of the compliance suite.
     *
     * @param model the model under test
     * @param parse parses JSON text into the model's node type
     */
    public static <N extends @Nullable Object> Stream<DynamicTest> contractTests(
            JsonModel<N> model, Function<String, N> parse) {
        return Stream.of(
                DynamicTest.dynamicTest("kinds", () -> checkKinds(model, parse)),
                DynamicTest.dynamicTest("object members", () -> checkMembers(model, parse)),
                DynamicTest.dynamicTest("missing members and JSON null", () -> checkMissingAndNull(model, parse)),
                DynamicTest.dynamicTest("array elements", () -> checkElements(model, parse)),
                DynamicTest.dynamicTest("strings", () -> checkStrings(model, parse)),
                DynamicTest.dynamicTest("numbers", () -> checkNumbers(model, parse)),
                DynamicTest.dynamicTest("booleans", () -> checkBooleans(model, parse)),
                DynamicTest.dynamicTest("documents equal the reference model", () -> checkDocuments(model, parse)));
    }

    // ---- compliance suite ----

    private static String readSuite() {
        try (InputStream in = JsonModelTestKit.class.getResourceAsStream(CTS)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + CTS);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ComplianceCase toCase(Map<?, ?> test) {
        boolean invalid = Boolean.TRUE.equals(test.get("invalid_selector"));
        List<List<@Nullable Object>> results = new ArrayList<>();
        List<List<String>> paths = new ArrayList<>();
        if (test.containsKey("result")) {
            results.add(values(test.get("result")));
            if (test.get("result_paths") instanceof List<?> resultPaths) {
                paths.add(strings(resultPaths));
            }
        } else if (test.get("results") instanceof List<?> alternatives) {
            for (Object alternative : alternatives) {
                results.add(values(alternative));
            }
            if (test.get("results_paths") instanceof List<?> alternativePaths) {
                for (Object alternative : alternativePaths) {
                    paths.add(strings((List<?>) alternative));
                }
            }
        }
        return new ComplianceCase(
                (String) Objects.requireNonNull(test.get("name")),
                (String) Objects.requireNonNull(test.get("selector")),
                invalid,
                test.containsKey("document") ? JsonText.write(test.get("document")) : null,
                List.copyOf(results),
                List.copyOf(paths));
    }

    private static List<@Nullable Object> values(@Nullable Object list) {
        return new ArrayList<>((List<?>) list);
    }

    private static List<String> strings(List<?> list) {
        List<String> strings = new ArrayList<>();
        for (Object s : list) {
            strings.add((String) s);
        }
        return List.copyOf(strings);
    }

    private static <N extends @Nullable Object> void runComplianceCase(
            ComplianceCase test, JsonModel<N> model, Function<String, N> parse) {
        if (test.invalidSelector()) {
            assertThrows(
                    JsonPathSyntaxException.class,
                    () -> JsonPath.compile(test.selector()),
                    "the selector should be rejected");
            return;
        }
        N document = parse.apply(requireDocument(test));
        NodeList<N> nodes = JsonPath.compile(test.selector()).query(document, model);
        List<N> actual = nodes.values();
        for (int i = 0; i < test.results().size(); i++) {
            boolean pathsMatch = test.resultPaths().isEmpty()
                    || nodes.paths().equals(test.resultPaths().get(i));
            if (pathsMatch && sameValues(model, actual, test.results().get(i))) {
                return;
            }
        }
        throw new AssertionError("Expected " + (test.results().size() == 1 ? "" : "one of ")
                + render(test.results()) + (test.resultPaths().isEmpty() ? "" : " at " + test.resultPaths())
                + ", but the query selected " + JsonText.write(plain(model, actual)) + " at " + nodes.paths());
    }

    // NullAway does not see that the elements of a List<N> may be null when N is a nullable type variable.
    @SuppressWarnings("NullAway")
    private static <N extends @Nullable Object> List<@Nullable Object> plain(JsonModel<N> model, List<N> values) {
        List<@Nullable Object> plain = new ArrayList<>();
        for (N value : values) {
            plain.add(plain(model, value));
        }
        return plain;
    }

    private static String requireDocument(ComplianceCase test) {
        String json = test.documentJson();
        if (json == null) {
            throw new IllegalStateException("The case '" + test.name() + "' has no document");
        }
        return json;
    }

    private static <N extends @Nullable Object> boolean sameValues(
            JsonModel<N> model, List<N> actual, List<@Nullable Object> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            if (!JsonModel.jsonEquals(model, actual.get(i), JavaObjectModel.INSTANCE, expected.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static String render(List<List<@Nullable Object>> alternatives) {
        List<String> rendered = new ArrayList<>();
        for (List<@Nullable Object> alternative : alternatives) {
            rendered.add(JsonText.write(alternative));
        }
        return alternatives.size() == 1 ? rendered.get(0) : rendered.toString();
    }

    /** Converts a value of the model to plain Java objects through the model's methods, for messages. */
    private static <N extends @Nullable Object> @Nullable Object plain(JsonModel<N> model, N value) {
        return switch (model.kind(value)) {
            case OBJECT -> {
                Map<String, @Nullable Object> map = new LinkedHashMap<>();
                for (Map.Entry<String, N> member : model.members(value)) {
                    map.put(member.getKey(), plain(model, member.getValue()));
                }
                yield map;
            }
            case ARRAY -> {
                List<@Nullable Object> list = new ArrayList<>();
                for (int i = 0; i < model.size(value); i++) {
                    list.add(plain(model, model.element(value, i)));
                }
                yield list;
            }
            case STRING -> model.stringValue(value);
            case NUMBER -> {
                BigDecimal number = model.numberValue(value);
                yield number == null ? "(not a finite number)" : number;
            }
            case BOOLEAN -> model.booleanValue(value);
            case NULL -> null;
        };
    }

    // ---- contract ----

    private static <N extends @Nullable Object> void checkKinds(JsonModel<N> model, Function<String, N> parse) {
        Map<String, JsonKind> kinds = new LinkedHashMap<>();
        kinds.put("{\"a\": 1}", JsonKind.OBJECT);
        kinds.put("{}", JsonKind.OBJECT);
        kinds.put("[1, 2]", JsonKind.ARRAY);
        kinds.put("[]", JsonKind.ARRAY);
        kinds.put("\"text\"", JsonKind.STRING);
        kinds.put("\"\"", JsonKind.STRING);
        kinds.put("0", JsonKind.NUMBER);
        kinds.put("-1.5e3", JsonKind.NUMBER);
        kinds.put("true", JsonKind.BOOLEAN);
        kinds.put("false", JsonKind.BOOLEAN);
        kinds.put("null", JsonKind.NULL);
        for (Map.Entry<String, JsonKind> kind : kinds.entrySet()) {
            assertEquals(kind.getValue(), model.kind(parse.apply(kind.getKey())), "kind of " + kind.getKey());
            N wrapped = parse.apply("[" + kind.getKey() + "]");
            assertEquals(kind.getValue(), model.kind(model.element(wrapped, 0)), "kind of an element " + kind.getKey());
        }
    }

    private static <N extends @Nullable Object> void checkMembers(JsonModel<N> model, Function<String, N> parse) {
        N object = parse.apply("{\"a\": 1, \"b\": \"x\", \"c\": [true], \"\": {}, \"é😀\": 2}");
        Set<String> expected = Set.of("a", "b", "c", "", "é😀");
        Set<String> names = new HashSet<>();
        model.memberNames(object).forEach(names::add);
        assertEquals(expected, names, "memberNames");
        assertEquals(expected.size(), model.memberCount(object), "memberCount");
        Set<String> entryNames = new HashSet<>();
        for (Map.Entry<String, N> member : model.members(object)) {
            entryNames.add(member.getKey());
            assertTrue(
                    JsonModel.jsonEquals(model, member.getValue(), model, model.member(object, member.getKey())),
                    "members() and member() agree on " + member.getKey());
        }
        assertEquals(expected, entryNames, "names of members()");
        for (String name : expected) {
            assertTrue(model.hasMember(object, name), "hasMember " + name);
            N member = model.findMember(object, name);
            assertNotNull(member, "findMember " + name);
        }
        assertEquals(JsonKind.STRING, model.kind(model.member(object, "b")));
        assertEquals("x", model.stringValue(model.member(object, "b")));
    }

    private static <N extends @Nullable Object> void checkMissingAndNull(
            JsonModel<N> model, Function<String, N> parse) {
        N object = parse.apply("{\"present\": null}");
        assertFalse(model.hasMember(object, "missing"), "hasMember of a missing member");
        assertNull(model.findMember(object, "missing"), "findMember of a missing member");
        assertTrue(model.hasMember(object, "present"), "hasMember of a member that is JSON null");
        // Models that represent JSON null as Java null return null here too; hasMember tells the two apart.
        N present = model.findMember(object, "present");
        if (present != null) {
            assertEquals(JsonKind.NULL, model.kind(present), "kind of a member that is JSON null");
        }
        assertEquals(JsonKind.NULL, model.kind(model.member(object, "present")));
    }

    private static <N extends @Nullable Object> void checkElements(JsonModel<N> model, Function<String, N> parse) {
        N array = parse.apply("[1, \"x\", null, [true], {}]");
        assertEquals(5, model.size(array), "size");
        JsonKind[] kinds = {JsonKind.NUMBER, JsonKind.STRING, JsonKind.NULL, JsonKind.ARRAY, JsonKind.OBJECT};
        for (int i = 0; i < kinds.length; i++) {
            assertEquals(kinds[i], model.kind(model.element(array, i)), "kind of element " + i);
        }
        assertEquals(0, model.size(parse.apply("[]")), "size of an empty array");
    }

    private static <N extends @Nullable Object> void checkStrings(JsonModel<N> model, Function<String, N> parse) {
        Map<String, String> strings = new LinkedHashMap<>();
        strings.put("\"\"", "");
        strings.put("\"text\"", "text");
        strings.put("\"é😀\"", "é😀");
        strings.put("\"\\u00e9\\ud83d\\ude00\"", "é😀");
        strings.put("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0000\"", "\"\\/\b\f\n\r\t\u0000");
        for (Map.Entry<String, String> string : strings.entrySet()) {
            assertEquals(string.getValue(), model.stringValue(parse.apply(string.getKey())), string.getKey());
        }
    }

    private static <N extends @Nullable Object> void checkNumbers(JsonModel<N> model, Function<String, N> parse) {
        String[] numbers = {
            "0",
            "-0",
            "1",
            "-1",
            "42",
            "2147483648",
            "-9223372036854775808",
            "1.5",
            "-0.25",
            "0.1",
            "1e2",
            "1E-7",
            "2.5e+3",
            "123456.789"
        };
        for (String json : numbers) {
            N number = parse.apply(json);
            BigDecimal expected = new BigDecimal(json);
            BigDecimal actual = model.numberValue(number);
            assertNotNull(actual, "numberValue of " + json);
            assertEquals(0, expected.compareTo(actual), "numberValue of " + json + " was " + actual);
            if (model.isLong(number)) {
                assertEquals(expected.longValueExact(), model.longValue(number), "longValue of " + json);
            }
        }
    }

    private static <N extends @Nullable Object> void checkBooleans(JsonModel<N> model, Function<String, N> parse) {
        assertTrue(model.booleanValue(parse.apply("true")), "true");
        assertFalse(model.booleanValue(parse.apply("false")), "false");
    }

    private static <N extends @Nullable Object> void checkDocuments(JsonModel<N> model, Function<String, N> parse) {
        int documents = 0;
        for (ComplianceCase test : complianceCases()) {
            String json = test.documentJson();
            if (json == null) {
                continue;
            }
            assertTrue(
                    JsonModel.jsonEquals(
                            model, parse.apply(json), JavaObjectModel.INSTANCE, JavaObjectModel.parse(json)),
                    "document of '" + test.name() + "': " + json);
            documents++;
        }
        assertTrue(documents > 0, "the suite has documents");
    }
}
