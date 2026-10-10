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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class HostileRegexBenchmark {

    /** The parameter and the document, shared by both libraries' states. */
    @State(Scope.Benchmark)
    public abstract static class Input {

        @Param({"10", "15", "20", "24"})
        public int length;

        Object document;

        void createDocument() {
            document = List.of(Map.of("s", "a".repeat(length) + "!"));
        }
    }

    /** As in {@link QueryBenchmark}, each library has its own state, so a benchmark's JVM only runs that library. */
    @State(Scope.Benchmark)
    public static class Rfc9535 extends Input {

        final JsonPath path = JsonPath.compile("$[?match(@.s, '((a+)+)+b')]");

        @Setup
        public void setup() {
            createDocument();
        }
    }

    /** The expression for Jayway JsonPath, which uses java.util.regex. */
    @State(Scope.Benchmark)
    public static class Jayway extends Input {

        final com.jayway.jsonpath.JsonPath path = com.jayway.jsonpath.JsonPath.compile("$[?(@.s =~ /((a+)+)+b/)]");
        final Configuration configuration = Configuration.defaultConfiguration();

        @Setup
        public void setup() {
            createDocument();
        }
    }

    @Benchmark
    public Object rfc9535(Rfc9535 state) {
        return state.path.query(state.document).values();
    }

    @Benchmark
    public Object jayway(Jayway state) {
        return state.path.read(state.document, state.configuration);
    }
}
