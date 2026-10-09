package io.github.treetrail.jsonpath.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.jackson2.Jackson2Model;
import java.util.ArrayList;
import java.util.List;
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
 * Both query the same {@link JsonNode} tree without converting it.
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
    private JsonPath rfc;
    private com.jayway.jsonpath.JsonPath jayway;
    private final Configuration jaywayConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

    @Setup
    public void setup() {
        document = new ObjectMapper().valueToTree(Documents.store(books));
        String rfcExpression;
        String jaywayExpression;
        switch (query) {
            case "definite":
                rfcExpression = "$.store.bicycle.color";
                jaywayExpression = rfcExpression;
                break;
            case "wildcard":
                rfcExpression = "$.store.book[*].title";
                jaywayExpression = rfcExpression;
                break;
            case "filter":
                rfcExpression = "$.store.book[?(@.price < 10 && @.category == 'fiction')].title";
                jaywayExpression = rfcExpression;
                break;
            case "descendant":
                rfcExpression = "$..price";
                jaywayExpression = rfcExpression;
                break;
            case "regex":
                rfcExpression = "$.store.book[?match(@.author, 'H.*')].title";
                jaywayExpression = "$.store.book[?(@.author =~ /H.*/)].title";
                break;
            default:
                throw new IllegalArgumentException(query);
        }
        rfc = JsonPath.compile(rfcExpression);
        jayway = com.jayway.jsonpath.JsonPath.compile(jaywayExpression);
        checkSameResult();
    }

    /** Guards the fairness of the comparison: both must select the same values. */
    private void checkSameResult() {
        List<JsonNode> ours = rfc.query(document, Jackson2Model.INSTANCE).values();
        JsonNode theirs = jayway.read(document, jaywayConfiguration);
        List<JsonNode> theirList = new ArrayList<>();
        if (theirs.isArray() && !query.equals("definite")) {
            theirs.forEach(theirList::add);
        } else {
            theirList.add(theirs);
        }
        if (ours.isEmpty() || !ours.equals(theirList)) {
            throw new IllegalStateException(query + ": results differ, " + ours.size() + " vs " + theirList.size());
        }
    }

    @Benchmark
    public Object rfc9535() {
        return rfc.query(document, Jackson2Model.INSTANCE).values();
    }

    @Benchmark
    public Object jayway() {
        return jayway.read(document, jaywayConfiguration);
    }
}
