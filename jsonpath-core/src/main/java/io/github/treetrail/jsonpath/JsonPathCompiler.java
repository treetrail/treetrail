package io.github.treetrail.jsonpath;

import io.github.treetrail.jsonpath.internal.FunctionDefinition;
import io.github.treetrail.jsonpath.internal.Functions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles queries with settings beyond {@link JsonPath#compile(String)}: function extensions and limits.
 * Immutable and thread-safe; each {@code with...} method returns a new compiler.
 *
 * <pre>{@code
 * JsonPathCompiler compiler = JsonPath.compiler().withFunctions(min, max);
 * JsonPath path = compiler.compile("$[?@.price == min($[*].price)]");
 * }</pre>
 */
public final class JsonPathCompiler {

    static final JsonPathCompiler DEFAULT = new JsonPathCompiler(Functions.BUILT_IN, EvaluationLimits.DEFAULT);

    private final Map<String, FunctionDefinition> functions;
    private final EvaluationLimits limits;

    private JsonPathCompiler(Map<String, FunctionDefinition> functions, EvaluationLimits limits) {
        this.functions = functions;
        this.limits = limits;
    }

    /**
     * Returns a compiler that also knows these functions.
     *
     * @throws IllegalArgumentException if a function has the name of one this compiler knows already
     */
    public JsonPathCompiler withFunctions(FunctionExtension... extensions) {
        Map<String, FunctionDefinition> combined = new LinkedHashMap<>(functions);
        for (FunctionExtension extension : extensions) {
            Objects.requireNonNull(extension, "extension");
            if (combined.putIfAbsent(extension.name(), extension.definition()) != null) {
                throw new IllegalArgumentException("A function named '" + extension.name() + "' is already known");
            }
        }
        return new JsonPathCompiler(Map.copyOf(combined), limits);
    }

    /** Returns a compiler whose queries run with these limits. */
    public JsonPathCompiler withLimits(EvaluationLimits limits) {
        return new JsonPathCompiler(functions, Objects.requireNonNull(limits, "limits"));
    }

    /**
     * Compiles a query.
     *
     * @throws JsonPathSyntaxException if {@code expression} is not a valid query, including calls of unknown
     *     functions and calls whose arguments do not fit the declared parameter types
     * @throws NullPointerException if {@code expression} is null
     */
    public JsonPath compile(String expression) {
        return JsonPath.compile(expression, functions, limits);
    }
}
