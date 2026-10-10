package io.github.treetrail.jsonpath.rewrite;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import io.github.treetrail.jsonpath.migration.MigrationHints;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Finds JSONPath expressions passed to Jayway JsonPath and to Spring's {@code jsonPath(...)} test
 * helpers (MockMvc and WebTestClient), and assesses each one against RFC 9535. Each call site is marked
 * with a hint, and the {@link JsonPathExpressions} data table lists every expression, so a migration can be
 * planned. Java and Kotlin sources are covered.
 *
 * <p>Expressions may be string literals, compile-time constants ({@code static final String} fields, also
 * of other classes and built from other constants with {@code +}) or, for Spring's helpers, format
 * templates such as {@code jsonPath("$.items[%d].name", 0)}, which are assessed with their arguments.
 *
 * <p>The recipe changes no code: whether a call can be migrated automatically depends on how its
 * result is used (Jayway returns a single value for definite paths, RFC 9535 always a node list).
 */
public final class FindJaywayJsonPathExpressions extends ScanningRecipe<FindJaywayJsonPathExpressions.Constants> {

    /** A method that takes a JSONPath expression, and the index of that argument. */
    private static final class Target {
        final MethodMatcher matcher;
        final int argument;
        final boolean staticOnly;
        final boolean write;
        /** Whether the expression is a {@code String.format} template for the arguments that follow it. */
        final boolean format;

        Target(String pattern, int argument, boolean staticOnly, boolean write, boolean format) {
            this.matcher = new MethodMatcher(pattern, true);
            this.argument = argument;
            this.staticOnly = staticOnly;
            this.write = write;
            this.format = format;
        }
    }

    private static final List<Target> TARGETS = List.of(
            new Target("com.jayway.jsonpath.JsonPath compile(String, ..)", 0, true, false, false),
            // Static JsonPath.read(json, path, filters...); the instance read(...) methods take no path.
            new Target("com.jayway.jsonpath.JsonPath read(*, String, ..)", 1, true, false, false),
            new Target("com.jayway.jsonpath.ReadContext read(String, ..)", 0, false, false, false),
            new Target("com.jayway.jsonpath.WriteContext set(String, ..)", 0, false, true, false),
            new Target("com.jayway.jsonpath.WriteContext put(String, ..)", 0, false, true, false),
            new Target("com.jayway.jsonpath.WriteContext add(String, ..)", 0, false, true, false),
            new Target("com.jayway.jsonpath.WriteContext delete(String, ..)", 0, false, true, false),
            new Target("com.jayway.jsonpath.WriteContext renameKey(String, ..)", 0, false, true, false),
            new Target("com.jayway.jsonpath.WriteContext map(String, ..)", 0, false, true, false),
            new Target(
                    "org.springframework.test.web.servlet.result.MockMvcResultMatchers jsonPath(String, ..)",
                    0,
                    true,
                    false,
                    true),
            new Target(
                    "org.springframework.test.web.reactive.server.WebTestClient$BodyContentSpec jsonPath(String, ..)",
                    0,
                    false,
                    false,
                    true));

    /** The {@code static final String} constants of all scanned sources, by owner and name. */
    public static final class Constants {
        final Map<String, Expression> initializers;
        /** Whether literals come from Kotlin, whose parser keeps the escape in {@code "\\$"}. */
        final boolean kotlin;

        Constants() {
            this(new HashMap<>(), false);
        }

        Constants(Constants scanned, boolean kotlin) {
            this(scanned.initializers, kotlin);
        }

        private Constants(Map<String, Expression> initializers, boolean kotlin) {
            this.initializers = initializers;
            this.kotlin = kotlin;
        }
    }

    private final transient JsonPathExpressions table = new JsonPathExpressions(this);

    @Override
    public String getDisplayName() {
        return "Find Jayway JsonPath expressions";
    }

    @Override
    public String getDescription() {
        return "Finds JSONPath expressions used with Jayway JsonPath and Spring's `jsonPath(...)` test helpers "
                + "and assesses each one against RFC 9535, with a migration hint per call site.";
    }

    @Override
    public Constants getInitialValue(ExecutionContext ctx) {
        return new Constants();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Constants constants) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(
                    J.VariableDeclarations declarations, ExecutionContext ctx) {
                J.VariableDeclarations d = super.visitVariableDeclarations(declarations, ctx);
                for (J.VariableDeclarations.NamedVariable variable : d.getVariables()) {
                    JavaType.Variable type = variable.getVariableType();
                    Expression initializer = variable.getInitializer();
                    if (type != null
                            && initializer != null
                            && type.hasFlags(Flag.Static, Flag.Final)
                            && TypeUtils.isString(type.getType())
                            && type.getOwner() instanceof JavaType.FullyQualified owner) {
                        constants.initializers.put(key(owner, type.getName()), initializer);
                    }
                }
                return d;
            }
        };
    }

    private static String key(JavaType.FullyQualified owner, String name) {
        return owner.getFullyQualifiedName() + "#" + name;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Constants constants) {
        @SuppressWarnings("unchecked")
        TreeVisitor<?, ExecutionContext>[] usesAnyTarget = TARGETS.stream()
                .map(t -> new UsesMethod<ExecutionContext>(t.matcher))
                .toArray(TreeVisitor[]::new);
        return Preconditions.check(Preconditions.or(usesAnyTarget), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                for (Target target : TARGETS) {
                    if (!target.matcher.matches(m)) {
                        continue;
                    }
                    JavaType.Method type = m.getMethodType();
                    if (target.staticOnly && (type == null || !type.hasFlags(Flag.Static))) {
                        continue;
                    }
                    if (m.getArguments().size() <= target.argument) {
                        continue;
                    }
                    String call = type == null
                            ? m.getSimpleName()
                            : type.getDeclaringType().getClassName() + "." + m.getSimpleName();
                    JavaSourceFile source = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
                    String sourcePath = source.getSourcePath().toString();
                    List<Expression> arguments = m.getArguments();
                    List<Expression> formatArguments = target.format
                            ? arguments.subList(target.argument + 1, arguments.size()).stream()
                                    .filter(a -> !(a instanceof J.Empty))
                                    .toList()
                            : List.of();
                    // The Kotlin parser keeps the escape in "\\$" in a literal's value.
                    boolean kotlin = source.getClass().getName().startsWith("org.openrewrite.kotlin.");
                    Assessment assessment = assess(
                            arguments.get(target.argument),
                            formatArguments,
                            target.write,
                            kotlin ? new Constants(constants, true) : constants);
                    table.insertRow(
                            ctx,
                            new JsonPathExpressions.Row(
                                    sourcePath, call, assessment.expression, assessment.kind, assessment.hint));
                    return SearchResult.found(m, assessment.kind + ": " + assessment.hint);
                }
                return m;
            }
        });
    }

    static final class Assessment {
        final @Nullable String expression;
        final String kind;
        final String hint;

        Assessment(@Nullable String expression, String kind, String hint) {
            this.expression = expression;
            this.kind = kind;
            this.hint = hint;
        }
    }

    static Assessment assess(Expression argument, boolean write) {
        return assess(argument, List.of(), write, new Constants());
    }

    static Assessment assess(
            Expression argument, List<Expression> formatArguments, boolean write, Constants constants) {
        Object value = constantValue(argument, constants, new HashSet<>());
        if (!(value instanceof String template)) {
            return new Assessment(
                    null, "NOT_A_LITERAL", "The expression is computed at runtime; check it with JaywayComparison.");
        }
        if (write) {
            return new Assessment(
                    template,
                    "WRITE_API",
                    "RFC 9535 defines queries only; select the nodes and modify the document with your JSON library.");
        }
        String expression = template;
        String note = "";
        if (!formatArguments.isEmpty()) {
            String formatted = format(template, formatArguments, constants);
            if (formatted != null) {
                expression = formatted;
                note = "Format template, assessed as " + formatted + ". ";
            }
        }
        try {
            JsonPath.compile(expression);
        } catch (JsonPathSyntaxException e) {
            return new Assessment(
                    template, "NOT_RFC_9535", note + MigrationHints.forInvalidExpression(expression, e.getMessage()));
        }
        if (isDefiniteForJayway(expression)) {
            return new Assessment(
                    template,
                    "VALID_SINGLE_VALUE",
                    note
                            + "Valid RFC 9535. Jayway returns a single value here, RFC 9535 a node list: use NodeList.single().");
        }
        return new Assessment(
                template,
                "VALID",
                note
                        + "Valid RFC 9535. Results can still differ (slices, type coercion); check with JaywayComparison.");
    }

    /**
     * Applies a format template to the arguments as Spring does ({@code String.format}). Arguments known at
     * compile time are used as they are; others get a sample value of their type: 0 for numbers, "a" for text.
     * Returns null if the template does not fit the arguments.
     */
    private static @Nullable String format(String template, List<Expression> arguments, Constants constants) {
        Object[] values = new Object[arguments.size()];
        for (int i = 0; i < values.length; i++) {
            Object value = constantValue(arguments.get(i), constants, new HashSet<>());
            values[i] = value != null ? value : sample(arguments.get(i).getType());
        }
        try {
            return String.format(Locale.ROOT, template, values);
        } catch (IllegalFormatException e) {
            return null;
        }
    }

    private static Object sample(@Nullable JavaType type) {
        if (type instanceof JavaType.Primitive primitive) {
            return switch (primitive) {
                case Boolean -> true;
                case Char, String -> "a";
                case Double, Float -> 0.0;
                default -> 0;
            };
        }
        if (TypeUtils.isString(type) || TypeUtils.isOfClassType(type, "java.lang.Character")) {
            return "a";
        }
        if (TypeUtils.isOfClassType(type, "java.lang.Boolean")) {
            return true;
        }
        return 0;
    }

    /**
     * The value of an expression known at compile time: a literal, a {@code static final} constant of a
     * scanned source, or a {@code +} of such values. Null if it is not known.
     */
    private static @Nullable Object constantValue(Expression expression, Constants constants, Set<String> resolving) {
        if (expression instanceof J.Literal literal) {
            Object value = literal.getValue();
            return constants.kotlin && value instanceof String string ? string.replace("\\$", "$") : value;
        }
        if (expression instanceof J.Parentheses<?> parentheses && parentheses.getTree() instanceof Expression inner) {
            return constantValue(inner, constants, resolving);
        }
        if (expression instanceof J.Binary binary && binary.getOperator() == J.Binary.Type.Addition) {
            Object left = constantValue(binary.getLeft(), constants, resolving);
            Object right = constantValue(binary.getRight(), constants, resolving);
            if (left instanceof String || right instanceof String) {
                return left == null || right == null ? null : String.valueOf(left) + right;
            }
            return null;
        }
        JavaType.Variable field = expression instanceof J.Identifier identifier
                ? identifier.getFieldType()
                : expression instanceof J.FieldAccess access ? access.getName().getFieldType() : null;
        if (field != null && field.getOwner() instanceof JavaType.FullyQualified owner) {
            String key = key(owner, field.getName());
            Expression initializer = constants.initializers.get(key);
            if (initializer != null && resolving.add(key)) {
                return constantValue(initializer, constants, resolving);
            }
        }
        return null;
    }

    private static boolean isDefiniteForJayway(String expression) {
        try {
            return com.jayway.jsonpath.JsonPath.isPathDefinite(expression);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
