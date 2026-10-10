package io.github.treetrail.jsonpath.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.jackson2.Jackson2Model;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * The queries of {@link QueryBenchmark} against a Jackson 2 tree, the most common setup in practice:
 * this library through {@link Jackson2Model}, Jayway JsonPath through its {@link JacksonJsonNodeJsonProvider}.
 * Both query the same {@link JsonNode} tree without converting it. As in {@link QueryBenchmark}, each library
 * has its own state, and {@code BenchmarkFairnessTest} checks that both select the same nodes.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class JacksonQueryBenchmark {

    /** The parameters and the document, shared by both libraries' states. */
    @State(Scope.Benchmark)
    public abstract static class Bookstore {

        @Param({"definite", "wildcard", "filter", "descendant", "regex"})
        public String query;

        @Param({"1000"})
        public int books;

        JsonNode document;
    }

    /** The tree and the expression compiled by this library. */
    @State(Scope.Benchmark)
    public static class Rfc9535 extends Bookstore {

        JsonPath path;

        @Setup
        public void setup() {
            document = new ObjectMapper().valueToTree(Documents.store(books));
            path = JsonPath.compile(Queries.rfc9535(query));
        }
    }

    /** The tree and the expression compiled by Jayway JsonPath, with its Jackson providers. */
    @State(Scope.Benchmark)
    public static class Jayway extends Bookstore {

        com.jayway.jsonpath.JsonPath path;
        final Configuration configuration = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .build();

        @Setup
        public void setup() {
            document = new ObjectMapper().valueToTree(Documents.store(books));
            path = com.jayway.jsonpath.JsonPath.compile(Queries.jayway(query));
        }
    }

    @Benchmark
    public Object rfc9535(Rfc9535 state) {
        return state.path.query(state.document, Jackson2Model.INSTANCE).values();
    }

    @Benchmark
    public Object jayway(Jayway state) {
        return state.path.read(state.document, state.configuration);
    }
}
