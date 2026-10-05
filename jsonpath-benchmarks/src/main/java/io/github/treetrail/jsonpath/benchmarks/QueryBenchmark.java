package io.github.treetrail.jsonpath.benchmarks;

import io.github.treetrail.jsonpath.JsonPath;
import com.jayway.jsonpath.Configuration;
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
 * Query throughput on a bookstore document: both libraries get the same precompiled expression and
 * the same document (plain Java objects, which Jayway's default json-smart provider reads directly).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryBenchmark {

    /** Expressions written so that both libraries accept them and select the same nodes. */
    @Param({"definite", "wildcard", "filter", "descendant", "regex"})
    public String query;

    @Param({"1000"})
    public int books;

    private Object document;
    private JsonPath rfc;
    private com.jayway.jsonpath.JsonPath jayway;
    private final Configuration jaywayConfiguration = Configuration.defaultConfiguration();

    @Setup
    public void setup() {
        document = Documents.store(books);
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
        List<Object> ours = rfc.query(document).values();
        Object theirs = jayway.read(document, jaywayConfiguration);
        List<?> theirList = theirs instanceof List ? (List<?>) theirs : List.of(theirs);
        if (!ours.equals(theirList)) {
            throw new IllegalStateException(query + ": results differ, " + ours.size() + " vs " + theirList.size());
        }
    }

    @Benchmark
    public Object rfc9535() {
        return rfc.query(document).values();
    }

    @Benchmark
    public Object jayway() {
        return jayway.read(document, jaywayConfiguration);
    }
}
