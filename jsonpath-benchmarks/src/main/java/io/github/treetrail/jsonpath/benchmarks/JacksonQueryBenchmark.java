package io.github.treetrail.jsonpath.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * The queries of {@link QueryBenchmark} against a Jackson 2 tree through {@link Jackson2Model}, the most
 * common setup in practice. Measures this library only.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class JacksonQueryBenchmark {

    @Param({"definite", "wildcard", "filter", "descendant", "regex"})
    public String query;

    @Param({"1000"})
    public int books;

    private JsonNode document;
    private JsonPath path;

    @Setup
    public void setup() {
        document = new ObjectMapper().valueToTree(Documents.store(books));
        switch (query) {
            case "definite":
                path = JsonPath.compile("$.store.bicycle.color");
                break;
            case "wildcard":
                path = JsonPath.compile("$.store.book[*].title");
                break;
            case "filter":
                path = JsonPath.compile("$.store.book[?(@.price < 10 && @.category == 'fiction')].title");
                break;
            case "descendant":
                path = JsonPath.compile("$..price");
                break;
            case "regex":
                path = JsonPath.compile("$.store.book[?match(@.author, 'H.*')].title");
                break;
            default:
                throw new IllegalArgumentException(query);
        }
    }

    @Benchmark
    public Object rfc9535() {
        return path.query(document, Jackson2Model.INSTANCE).values();
    }
}
