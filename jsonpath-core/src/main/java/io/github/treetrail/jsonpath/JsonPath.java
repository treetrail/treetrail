package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Ast;
import io.github.treetrail.jsonpath.internal.Evaluator;
import io.github.treetrail.jsonpath.internal.Functions;
import io.github.treetrail.jsonpath.internal.Located;
import io.github.treetrail.jsonpath.internal.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A compiled JSONPath query as defined by RFC 9535.
 *
 * <pre>{@code
 * JsonPath path = JsonPath.compile("$.store.book[?@.price < 10].title");
 * List<Object> titles = path.query(document).values();
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe; compile once and reuse.
 *
 * <p>Every run is bounded by {@link EvaluationLimits}, {@link EvaluationLimits#DEFAULT} unless
 * {@link #withLimits(EvaluationLimits)} sets others. For queries from untrusted sources, set limits that
 * fit the expected documents.
 */
public final class JsonPath {

    private final String expression;
    private final Ast.Query query;
    private final EvaluationLimits limits;

    private JsonPath(String expression, Ast.Query query, EvaluationLimits limits) {
        this.expression = expression;
        this.query = query;
        this.limits = limits;
    }

    /**
     * Compiles a query.
     *
     * @throws JsonPathSyntaxException if {@code expression} is not a valid RFC 9535 query
     */
    public static JsonPath compile(String expression) {
        return new JsonPath(expression, Parser.parse(expression, Functions.BUILT_IN), EvaluationLimits.DEFAULT);
    }

    /** Returns this query with other limits for running it. */
    public JsonPath withLimits(EvaluationLimits limits) {
        return new JsonPath(expression, query, Objects.requireNonNull(limits, "limits"));
    }

    /** Returns the limits for running this query. */
    public EvaluationLimits limits() {
        return limits;
    }

    /**
     * Runs the query against a document made of plain Java objects ({@link JavaObjectModel}).
     *
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits}
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public NodeList<Object> query(Object document) {
        return query(document, JavaObjectModel.INSTANCE);
    }

    /**
     * Runs the query against a document in the given object model.
     *
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits}
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public <N> NodeList<N> query(N document, JsonModel<N> model) {
        List<Located> located = new Evaluator(document, model, limits).run(query);
        List<Node<N>> nodes = new ArrayList<>(located.size());
        for (Located l : located) {
            @SuppressWarnings("unchecked")
            N value = (N) l.value();
            nodes.add(new Node<>(value, l.location()));
        }
        return new NodeList<>(nodes);
    }

    /** Whether the query is singular, that is, selects at most one node (RFC 9535, section 2.3.5.1). */
    public boolean isSingular() {
        return query.isSingular();
    }

    @Override
    public String toString() {
        return expression;
    }
}
