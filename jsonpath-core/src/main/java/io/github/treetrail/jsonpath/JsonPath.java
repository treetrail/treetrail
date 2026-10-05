package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.Ast;
import io.github.treetrail.jsonpath.internal.Evaluator;
import io.github.treetrail.jsonpath.internal.Functions;
import io.github.treetrail.jsonpath.internal.Located;
import io.github.treetrail.jsonpath.internal.Parser;
import java.util.ArrayList;
import java.util.List;

/**
 * A compiled JSONPath query as defined by RFC 9535.
 *
 * <pre>{@code
 * JsonPath path = JsonPath.compile("$.store.book[?@.price < 10].title");
 * List<Object> titles = path.query(document).values();
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe; compile once and reuse.
 */
public final class JsonPath {

    private final String expression;
    private final Ast.Query query;

    private JsonPath(String expression, Ast.Query query) {
        this.expression = expression;
        this.query = query;
    }

    /**
     * Compiles a query.
     *
     * @throws JsonPathSyntaxException if {@code expression} is not a valid RFC 9535 query
     */
    public static JsonPath compile(String expression) {
        return new JsonPath(expression, Parser.parse(expression, Functions.BUILT_IN));
    }

    /**
     * Runs the query against a document made of plain Java objects ({@link JavaObjectModel}).
     */
    public NodeList<Object> query(Object document) {
        return query(document, JavaObjectModel.INSTANCE);
    }

    /**
     * Runs the query against a document in the given object model.
     */
    public <N> NodeList<N> query(N document, JsonModel<N> model) {
        List<Located> located = new Evaluator(document, model).run(query);
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
