package io.github.treetrail.jsonpath.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.jmh.annotations.Param;

/**
 * Guards the fairness of the query comparisons: for every benchmarked query, both libraries select the same
 * values. This runs here rather than in the benchmarks' setup, so that a benchmark's JVM only runs the library
 * it measures.
 */
class BenchmarkFairnessTest {

    /** The queries benchmarked, read from the {@code @Param} annotation so that none is left out. */
    static Stream<String> queries() throws NoSuchFieldException {
        Param param = QueryBenchmark.Bookstore.class.getField("query").getAnnotation(Param.class);
        return Arrays.stream(param.value());
    }

    @ParameterizedTest
    @MethodSource("queries")
    void bothSelectTheSameValuesOnJavaObjects(String query) throws NoSuchFieldException {
        QueryBenchmark benchmark = new QueryBenchmark();
        QueryBenchmark.Rfc9535 ours = new QueryBenchmark.Rfc9535();
        QueryBenchmark.Jayway theirs = new QueryBenchmark.Jayway();
        for (QueryBenchmark.Bookstore state : List.of(ours, theirs)) {
            state.query = query;
            state.books = books();
        }
        ours.setup();
        theirs.setup();

        Object ourResult = benchmark.rfc9535(ours);
        Object theirResult = benchmark.jayway(theirs);

        List<?> theirList = theirResult instanceof List<?> list ? list : List.of(theirResult);
        assertThat(ourResult).asInstanceOf(LIST).isNotEmpty().isEqualTo(theirList);
    }

    @ParameterizedTest
    @MethodSource("queries")
    void bothSelectTheSameNodesOnJacksonTrees(String query) throws NoSuchFieldException {
        JacksonQueryBenchmark benchmark = new JacksonQueryBenchmark();
        JacksonQueryBenchmark.Rfc9535 ours = new JacksonQueryBenchmark.Rfc9535();
        JacksonQueryBenchmark.Jayway theirs = new JacksonQueryBenchmark.Jayway();
        for (JacksonQueryBenchmark.Bookstore state : List.of(ours, theirs)) {
            state.query = query;
            state.books = books();
        }
        ours.setup();
        theirs.setup();

        Object ourResult = benchmark.rfc9535(ours);
        JsonNode theirResult = (JsonNode) benchmark.jayway(theirs);

        List<JsonNode> theirList = new ArrayList<>();
        if (theirResult.isArray() && !query.equals("definite")) {
            theirResult.forEach(theirList::add);
        } else {
            theirList.add(theirResult);
        }
        assertThat(ourResult).asInstanceOf(LIST).isNotEmpty().isEqualTo(theirList);
    }

    @ParameterizedTest
    @MethodSource("queries")
    void otherImplementationsSelectTheSameValues(String query) throws Exception {
        QueryBenchmark.Rfc9535 ours = new QueryBenchmark.Rfc9535();
        OtherImplementationsBenchmark.Sjf4j sjf4j = new OtherImplementationsBenchmark.Sjf4j();
        OtherImplementationsBenchmark.Ajp ajp = new OtherImplementationsBenchmark.Ajp();
        for (QueryBenchmark.Bookstore state : List.of(ours, sjf4j, ajp)) {
            state.query = query;
            state.books = books();
        }
        ours.setup();
        sjf4j.setup();
        ajp.setup();
        OtherImplementationsBenchmark others = new OtherImplementationsBenchmark();

        Object ourResult = new QueryBenchmark().rfc9535(ours);
        Object sjf4jResult = others.sjf4j(sjf4j);
        XdmValue ajpNodes = (XdmValue) others.ajp(ajp);

        assertThat(ourResult).asInstanceOf(LIST).isNotEmpty();
        if (query.equals("regex")) {
            // SJF4J 1.3.3 selects nothing when the pattern of match() has a quantifier (even '.*'), so this query
            // has no comparable SJF4J time; docs/benchmarks.md says so. Fails once SJF4J selects the nodes.
            assertThat(sjf4jResult).as("SJF4J").asInstanceOf(LIST).isEmpty();
        } else {
            assertThat(sjf4jResult).as("SJF4J").isEqualTo(ourResult);
        }
        ObjectMapper mapper = new ObjectMapper();
        // Compared as text, numbers by value: Saxon writes the number 26.0 as 26.
        List<String> ajpValues = new ArrayList<>();
        mapper.readTree(ajp.runner.serializeToJson(ajp.runner.arrayOfValues(ajpNodes)))
                .forEach(node -> ajpValues.add(text(node)));
        List<String> ourValues = new ArrayList<>();
        mapper.<JsonNode>valueToTree(ourResult).forEach(node -> ourValues.add(text(node)));
        if (query.equals("descendant")) {
            // ajp's maps (fn:parse-json) have no member order, and RFC 9535 leaves the order of an object's
            // members open, so the descendant query may list the same values in another order.
            assertThat(ajpValues).as("ajp").containsExactlyInAnyOrderElementsOf(ourValues);
        } else {
            assertThat(ajpValues).as("ajp").isEqualTo(ourValues);
        }
    }

    @ParameterizedTest
    @MethodSource("queries")
    void bothSelectTheSameValuesOnTheLargeDocument(String query) throws NoSuchFieldException {
        LargeDocumentBenchmark benchmark = new LargeDocumentBenchmark();
        LargeDocumentBenchmark.Rfc9535 ours = new LargeDocumentBenchmark.Rfc9535();
        LargeDocumentBenchmark.Jayway theirs = new LargeDocumentBenchmark.Jayway();
        for (LargeDocumentBenchmark.LargeBookstore state : List.of(ours, theirs)) {
            state.query = query;
            state.books = largeBooks();
        }
        ours.setup();
        theirs.setup();

        Object ourResult = benchmark.rfc9535(ours);
        Object theirResult = benchmark.jayway(theirs);

        List<?> theirList = theirResult instanceof List<?> list ? list : List.of(theirResult);
        assertThat(ourResult).asInstanceOf(LIST).isNotEmpty().isEqualTo(theirList);
    }

    @Test
    void theLargeDocumentHasAboutTenMegabytes() throws NoSuchFieldException {
        assertThat(Documents.json(largeBooks()).length()).isBetween(10_000_000, 11_000_000);
    }

    private static String text(JsonNode node) {
        return node.isNumber() ? node.decimalValue().stripTrailingZeros().toPlainString() : node.toString();
    }

    private static int largeBooks() throws NoSuchFieldException {
        Param param =
                LargeDocumentBenchmark.LargeBookstore.class.getField("books").getAnnotation(Param.class);
        return Integer.parseInt(param.value()[0]);
    }

    /** The document size benchmarked. */
    private static int books() throws NoSuchFieldException {
        Param param = QueryBenchmark.Bookstore.class.getField("books").getAnnotation(Param.class);
        return Integer.parseInt(param.value()[0]);
    }
}
