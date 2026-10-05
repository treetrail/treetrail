package io.github.treetrail.jsonpath.benchmarks;

import io.github.treetrail.jsonpath.JsonPath;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Cost of compiling an expression, for code that does not cache compiled paths.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CompileBenchmark {

    private static final String EXPRESSION = "$.store.book[?(@.price < 10 && @.category == 'fiction')].title";

    @Benchmark
    public Object rfc9535() {
        return JsonPath.compile(EXPRESSION);
    }

    @Benchmark
    public Object jayway() {
        return com.jayway.jsonpath.JsonPath.compile(EXPRESSION);
    }
}
