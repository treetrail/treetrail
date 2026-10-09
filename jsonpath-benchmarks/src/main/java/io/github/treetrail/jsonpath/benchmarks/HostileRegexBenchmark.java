package io.github.treetrail.jsonpath.benchmarks;

import com.jayway.jsonpath.Configuration;
import io.github.treetrail.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * A regular expression with catastrophic backtracking, {@code ((a+)+)+b}, applied to {@code length}
 * characters 'a' followed by '!'. A backtracking engine needs time exponential in the length;
 * an automaton needs linear time.
 *
 * <p>Since JDK 9, java.util.regex defuses many textbook cases such as {@code (a|a)*b} or
 * {@code (a+)+b}; triply nested quantifiers are still exponential on JDK 25.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class HostileRegexBenchmark {

    @Param({"10", "15", "20", "24"})
    public int length;

    private Object document;
    private final JsonPath rfc = JsonPath.compile("$[?match(@.s, '((a+)+)+b')]");
    private final com.jayway.jsonpath.JsonPath jayway =
            com.jayway.jsonpath.JsonPath.compile("$[?(@.s =~ /((a+)+)+b/)]");
    private final Configuration jaywayConfiguration = Configuration.defaultConfiguration();

    @Setup
    public void setup() {
        document = List.of(Map.of("s", "a".repeat(length) + "!"));
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
