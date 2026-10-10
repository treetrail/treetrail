package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Ast;
import io.github.treetrail.jsonpath.internal.Evaluator;
import io.github.treetrail.jsonpath.internal.FunctionDefinition;
import io.github.treetrail.jsonpath.internal.Located;
import io.github.treetrail.jsonpath.internal.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
    private final Map<String, FunctionDefinition> functions;
    private final EvaluationLimits limits;

    private JsonPath(
            String expression, Ast.Query query, Map<String, FunctionDefinition> functions, EvaluationLimits limits) {
        this.expression = expression;
        this.query = query;
        this.functions = functions;
        this.limits = limits;
    }

    /**
     * Compiles a query.
     *
     * @throws JsonPathSyntaxException if {@code expression} is not a valid RFC 9535 query
     * @throws NullPointerException if {@code expression} is null
     */
    public static JsonPath compile(String expression) {
        return JsonPathCompiler.DEFAULT.compile(expression);
    }

    /**
     * Returns a compiler for queries with function extensions or other settings.
     *
     * @see FunctionExtension
     */
    public static JsonPathCompiler compiler() {
        return JsonPathCompiler.DEFAULT;
    }

    static JsonPath compile(String expression, Map<String, FunctionDefinition> functions, EvaluationLimits limits) {
        Objects.requireNonNull(expression, "expression");
        return new JsonPath(expression, Parser.parse(expression, functions), functions, limits);
    }

    /** Returns this query with other limits for running it. */
    public JsonPath withLimits(EvaluationLimits limits) {
        return new JsonPath(expression, query, functions, Objects.requireNonNull(limits, "limits"));
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
    public NodeList<@Nullable Object> query(@Nullable Object document) {
        return query(document, JavaObjectModel.INSTANCE);
    }

    /**
     * Parses JSON text with {@link JavaObjectModel#parse(String)} and runs the query against it.
     *
     * <pre>{@code
     * List<Object> titles = JsonPath.compile("$.store.book[*].title").queryJson(responseBody).values();
     * }</pre>
     *
     * @throws InvalidJsonException if {@code json} is not valid JSON text
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits}
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public NodeList<@Nullable Object> queryJson(String json) {
        return query(JavaObjectModel.parse(json));
    }

    /**
     * Runs the query against a document in the given object model.
     *
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits}
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public <N extends @Nullable Object> NodeList<N> query(N document, JsonModel<N> model) {
        List<Located> located = new Evaluator(document, model, limits).run(query);
        List<Node<N>> nodes = new ArrayList<>(located.size());
        for (Located l : located) {
            @SuppressWarnings("unchecked")
            N value = (N) l.value();
            nodes.add(new Node<>(value, l.location()));
        }
        return new NodeList<>(nodes);
    }

    /**
     * Whether the query selects at least one node in a document made of plain Java objects. Stops at the
     * first node, so it is cheaper than {@code query(document).isEmpty()} for queries that select many.
     *
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits} before
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public boolean exists(@Nullable Object document) {
        return exists(document, JavaObjectModel.INSTANCE);
    }

    /**
     * Whether the query selects at least one node in a document of the given model; stops at the first.
     *
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits} before
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public <N extends @Nullable Object> boolean exists(N document, JsonModel<N> model) {
        return new Evaluator(document, model, limits).exists(query);
    }

    /**
     * Returns the first node the query selects in a document made of plain Java objects, in the order of
     * {@link #query(Object)}, without selecting the others.
     *
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits} before
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public Optional<Node<@Nullable Object>> first(@Nullable Object document) {
        return first(document, JavaObjectModel.INSTANCE);
    }

    /**
     * Returns the first node the query selects in a document of the given model, without selecting the others.
     *
     * @throws JsonPathLimitExceededException if the run exceeds the {@link #limits() limits} before
     * @throws JsonPathEvaluationException if the thread is interrupted
     */
    public <N extends @Nullable Object> Optional<Node<N>> first(N document, JsonModel<N> model) {
        Located first = new Evaluator(document, model, limits).first(query);
        if (first == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        N value = (N) first.value();
        return Optional.of(new Node<>(value, first.location()));
    }

    /** Whether the query is singular, that is, selects at most one node (RFC 9535, section 2.3.5.1). */
    public boolean isSingular() {
        return query.isSingular();
    }

    /** Returns the expression this query was compiled from. */
    public String expression() {
        return expression;
    }

    /**
     * Two queries are equal if they were compiled from the same expression with the same function extensions
     * and have the same limits.
     */
    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof JsonPath other
                && expression.equals(other.expression)
                && limits.equals(other.limits)
                && functions.equals(other.functions);
    }

    @Override
    public int hashCode() {
        return 31 * expression.hashCode() + limits.hashCode();
    }

    /** Returns the expression this query was compiled from. */
    @Override
    public String toString() {
        return expression;
    }
}
