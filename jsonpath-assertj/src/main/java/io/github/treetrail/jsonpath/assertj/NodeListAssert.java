package io.github.treetrail.jsonpath.assertj;

import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.NodeList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.assertj.core.api.ObjectAssert;

/**
 * Assertions on the values a JSONPath query selected, in document order. All list assertions of AssertJ
 * apply ({@code containsExactly}, {@code hasSize}, {@code isEmpty}, ...), with values compared as JSON
 * values: numbers by value and objects by members regardless of order.
 */
public final class NodeListAssert extends AbstractListAssert<NodeListAssert, List<?>, Object, ObjectAssert<Object>> {

    /** JSON equality as the comparator AssertJ uses for elements; the order of unequal values is arbitrary. */
    static final Comparator<Object> JSON_VALUES = new Comparator<>() {
        @Override
        public int compare(Object a, Object b) {
            return JavaObjectModel.jsonEquals(a, b)
                    ? 0
                    : Integer.compare(Objects.hashCode(String.valueOf(a)), Objects.hashCode(String.valueOf(b))) | 1;
        }

        @Override
        public String toString() {
            // AssertJ names the comparator in failure messages.
            return "JSON value equality (numbers by value)";
        }
    };

    private final List<String> paths;
    private final String expression;

    NodeListAssert(NodeList<?> nodes, String expression) {
        this(new ArrayList<Object>(nodes.values()), nodes.paths(), expression);
    }

    // usingElementComparator configures this assertion and returns it; the result is this object.
    @SuppressWarnings("CheckReturnValue")
    private NodeListAssert(List<?> values, List<String> paths, String expression) {
        super(values, NodeListAssert.class);
        this.paths = paths;
        this.expression = expression;
        usingElementComparator(JSON_VALUES);
    }

    /**
     * Verifies that the query selected exactly one node and continues with assertions on its value.
     * The value of a JSON {@code null} is {@code null}.
     */
    public ObjectAssert<Object> singleValue() {
        if (actual.size() != 1) {
            throw failure(
                    "Expected %s to select exactly one node, but it selected %d: %s at %s",
                    expression, actual.size(), actual, paths);
        }
        return Assertions.assertThat((Object) actual.get(0))
                .as(descriptionText().isEmpty() ? expression : descriptionText())
                .usingComparator(JSON_VALUES);
    }

    /** Continues with assertions on the normalized paths of the selected nodes, for example {@code $['a'][0]}. */
    public ListAssert<String> paths() {
        return Assertions.assertThat(paths).as("paths of %s", expression);
    }

    @Override
    protected ObjectAssert<Object> toAssert(Object value, String description) {
        return new ObjectAssert<>(value).as(description).usingComparator(JSON_VALUES);
    }

    @Override
    protected NodeListAssert newAbstractIterableAssert(Iterable<?> iterable) {
        List<Object> values = new ArrayList<>();
        iterable.forEach(values::add);
        return new NodeListAssert(values, values.size() == actual.size() ? paths : List.of(), expression);
    }
}
