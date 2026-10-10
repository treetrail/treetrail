package io.github.treetrail.jsonpath.benchmarks;

import java.util.concurrent.TimeUnit;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmFunctionItem;
import net.sf.saxon.s9api.XdmValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.xmljacquard.ajp.AjpRunner;

/**
 * The queries of {@link QueryBenchmark} with other Java implementations of RFC 9535, each on its own
 * representation of the same bookstore document, parsed and compiled in the setup:
 *
 * <ul>
 *   <li><a href="https://github.com/sjf4j-projects/sjf4j">SJF4J</a> queries the plain Java objects, like this
 *       library in {@code QueryBenchmark.rfc9535};
 *   <li><a href="https://github.com/xmljacquard/ajp">ajp</a> implements RFC 9535 in XSLT on Saxon and queries
 *       the document as XPath maps and arrays ({@code fn:parse-json}).
 * </ul>
 *
 * <p>{@code BenchmarkFairnessTest} checks that they select the same values as this library.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class OtherImplementationsBenchmark {

    /** SJF4J on the plain Java objects. */
    @State(Scope.Benchmark)
    public static class Sjf4j extends QueryBenchmark.Bookstore {

        org.sjf4j.path.JsonPath path;

        @Setup
        public void setup() {
            document = Documents.store(books);
            path = org.sjf4j.path.JsonPath.parse(Queries.rfc9535(query));
        }
    }

    /** ajp on the document as XPath maps and arrays. */
    @State(Scope.Benchmark)
    public static class Ajp extends QueryBenchmark.Bookstore {

        AjpRunner runner;
        XdmValue json;

        @Setup
        public void setup() throws Exception {
            runner = new AjpRunner().withQuery(Queries.rfc9535(query));
            json = parseJson(Documents.json(books));
        }
    }

    /** Parses JSON with XPath's {@code fn:parse-json}, as {@link AjpRunner#getNodelist(String)} does. */
    static XdmValue parseJson(String text) throws SaxonApiException {
        Processor processor = new Processor(false);
        XdmFunctionItem parseJson = XdmFunctionItem.getSystemFunction(
                processor, new QName("http://www.w3.org/2005/xpath-functions", "parse-json"), 1);
        return parseJson.call(processor, new XdmAtomicValue(text));
    }

    @Benchmark
    public Object sjf4j(Sjf4j state) {
        return state.path.find(state.document);
    }

    @Benchmark
    public Object ajp(Ajp state) throws SaxonApiException {
        return state.runner.getNodelist(state.json);
    }
}
