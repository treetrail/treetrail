package io.github.treetrail.jsonpath.benchmarks;

import com.jayway.jsonpath.Configuration;
import io.github.treetrail.jsonpath.JsonPath;
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
 *
 * <p>Each library has its own state, so that a benchmark's JVM only runs the library it measures: running
 * the other library's query during setup changes which code the JIT compiler sees first and measurably
 * shifted Jayway's wildcard time. {@code BenchmarkFairnessTest} checks that both select the same values.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryBenchmark {

    /** The parameters and the document, shared by both libraries' states. */
    @State(Scope.Benchmark)
    public abstract static class Bookstore {

        @Param({"definite", "wildcard", "filter", "descendant", "regex"})
        public String query;

        @Param({"1000"})
        public int books;

        Object document;
    }

    /** The document and the expression compiled by this library. */
    @State(Scope.Benchmark)
    public static class Rfc9535 extends Bookstore {

        JsonPath path;

        @Setup
        public void setup() {
            document = Documents.store(books);
            path = JsonPath.compile(Queries.rfc9535(query));
        }
    }

    /** The document and the expression compiled by Jayway JsonPath, with its default configuration. */
    @State(Scope.Benchmark)
    public static class Jayway extends Bookstore {

        com.jayway.jsonpath.JsonPath path;
        final Configuration configuration = Configuration.defaultConfiguration();

        @Setup
        public void setup() {
            document = Documents.store(books);
            path = com.jayway.jsonpath.JsonPath.compile(Queries.jayway(query));
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
