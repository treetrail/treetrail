package io.github.treetrail.jsonpath.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
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

    /** The document size benchmarked. */
    private static int books() throws NoSuchFieldException {
        Param param = QueryBenchmark.Bookstore.class.getField("books").getAnnotation(Param.class);
        return Integer.parseInt(param.value()[0]);
    }
}
