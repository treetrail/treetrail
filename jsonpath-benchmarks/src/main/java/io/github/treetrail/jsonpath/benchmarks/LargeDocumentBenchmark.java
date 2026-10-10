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
 * The queries of {@link QueryBenchmark} on a bookstore with 92,000 books, about 10 MB as JSON, as plain Java
 * objects. Measures how the query time grows with the document; parsing is not included.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class LargeDocumentBenchmark {

    /** The parameters and the document, shared by both libraries' states. */
    @State(Scope.Benchmark)
    public abstract static class LargeBookstore {

        @Param({"definite", "wildcard", "filter", "descendant", "regex"})
        public String query;

        @Param({"92000"})
        public int books;

        Object document;
    }

    /** The document and the expression compiled by this library. */
    @State(Scope.Benchmark)
    public static class Rfc9535 extends LargeBookstore {

        JsonPath path;

        @Setup
        public void setup() {
            document = Documents.store(books);
            path = JsonPath.compile(Queries.rfc9535(query));
        }
    }

    /** The document and the expression compiled by Jayway JsonPath, with its default configuration. */
    @State(Scope.Benchmark)
    public static class Jayway extends LargeBookstore {

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
