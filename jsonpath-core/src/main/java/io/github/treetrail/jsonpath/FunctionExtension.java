package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.FunctionDefinition;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.LogicalFunction;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.NodesFunction;
import io.github.treetrail.jsonpath.internal.FunctionDefinition.ValueFunction;
import io.github.treetrail.jsonpath.internal.Functions;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A function extension (RFC 9535, section 2.4): a function for filter expressions beyond the five the RFC
 * defines, with typed parameters and a typed result. Pass it to {@link JsonPathCompiler#withFunctions} to use
 * it in the queries that compiler compiles; there is no global registry.
 *
 * <pre>{@code
 * FunctionExtension min = FunctionExtension.value("min", List.of(FunctionType.NODES), args -> {
 *     BigDecimal smallest = null;
 *     for (FunctionValue v : args.nodes(0)) {
 *         if (v.kind() == JsonKind.NUMBER && (smallest == null || v.number().compareTo(smallest) < 0)) {
 *             smallest = v.number();
 *         }
 *     }
 *     return smallest == null ? FunctionValue.nothing() : FunctionValue.of(smallest);
 * });
 *
 * JsonPath path = JsonPath.compiler().withFunctions(min).compile("$.book[?@.price == min($.book[*].price)]");
 * }</pre>
 *
 * <p>Calls are type-checked against the declared types when a query is compiled, as for the built-in functions
 * (RFC 9535, section 2.4.3): {@code min('a')} is rejected, because a literal is not a node list. A function that
 * throws makes the query fail with a {@link JsonPathEvaluationException} naming the node being tested. Queries
 * that use an extension are not portable: other implementations do not know it.
 */
public final class FunctionExtension {

    /** {@code function-name} of RFC 9535, section 2.4: a lower-case letter, then letters, digits or {@code _}. */
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

    private final FunctionDefinition definition;

    private FunctionExtension(FunctionDefinition definition) {
        this.definition = definition;
    }

    /**
     * Declares a function of {@link FunctionType#VALUE}. The body returns a value, one of the arguments or
     * {@link FunctionValue#nothing()}.
     *
     * @throws IllegalArgumentException if the name is not a valid function name or is a built-in function
     */
    public static FunctionExtension value(
            String name, List<FunctionType> parameters, Function<FunctionArguments, FunctionValue> body) {
        Objects.requireNonNull(body, "body");
        return new FunctionExtension(
                new FunctionDefinition(checkName(name), copy(parameters), new ValueFunction(body)));
    }

    /**
     * Declares a function of {@link FunctionType#LOGICAL}, usable as a test in filters.
     *
     * @throws IllegalArgumentException if the name is not a valid function name or is a built-in function
     */
    public static FunctionExtension logical(
            String name, List<FunctionType> parameters, Predicate<FunctionArguments> body) {
        Objects.requireNonNull(body, "body");
        return new FunctionExtension(
                new FunctionDefinition(checkName(name), copy(parameters), new LogicalFunction(body)));
    }

    /**
     * Declares a function of {@link FunctionType#NODES}. The body returns the values of the nodes it selects;
     * each is one of the arguments' values or created with {@link FunctionValue}'s factories.
     *
     * @throws IllegalArgumentException if the name is not a valid function name or is a built-in function
     */
    public static FunctionExtension nodes(
            String name, List<FunctionType> parameters, Function<FunctionArguments, List<FunctionValue>> body) {
        Objects.requireNonNull(body, "body");
        return new FunctionExtension(
                new FunctionDefinition(checkName(name), copy(parameters), new NodesFunction(body)));
    }

    private static String checkName(String name) {
        Objects.requireNonNull(name, "name");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Not a function name (a lower-case letter, then letters, digits or _): " + name);
        }
        if (Functions.BUILT_IN.containsKey(name)) {
            throw new IllegalArgumentException("'" + name + "' is a built-in function of RFC 9535");
        }
        return name;
    }

    private static List<FunctionType> copy(List<FunctionType> parameters) {
        return List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    /** Returns the name, as used in queries. */
    public String name() {
        return definition.name();
    }

    /** Returns the parameter types. */
    public List<FunctionType> parameters() {
        return definition.parameters();
    }

    /** Returns the result type. */
    public FunctionType result() {
        return definition.result();
    }

    FunctionDefinition definition() {
        return definition;
    }

    @Override
    public String toString() {
        return name() + parameters() + " -> " + result();
    }
}
