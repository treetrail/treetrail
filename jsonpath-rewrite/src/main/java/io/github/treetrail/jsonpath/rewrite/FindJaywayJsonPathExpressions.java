package io.github.treetrail.jsonpath.rewrite;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import io.github.treetrail.jsonpath.migration.MigrationHints;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

/**
 * Finds JSONPath expressions passed to Jayway JsonPath and to Spring's {@code jsonPath(...)} test
 * helpers, and assesses each one against RFC 9535. Each call site is marked with a hint, and the
 * {@link JsonPathExpressions} data table lists every expression, so a migration can be planned.
 *
 * <p>The recipe changes no code: whether a call can be migrated automatically depends on how its
 * result is used (Jayway returns a single value for definite paths, RFC 9535 always a node list).
 */
public final class FindJaywayJsonPathExpressions extends Recipe {

    /** A method that takes a JSONPath expression, and the index of that argument. */
    private static final class Target {
        final MethodMatcher matcher;
        final int argument;
        final boolean staticOnly;
        final boolean write;

        Target(String pattern, int argument, boolean staticOnly, boolean write) {
            this.matcher = new MethodMatcher(pattern, true);
            this.argument = argument;
            this.staticOnly = staticOnly;
            this.write = write;
        }
    }

    private static final List<Target> TARGETS = List.of(
            new Target("com.jayway.jsonpath.JsonPath compile(String, ..)", 0, true, false),
            // Static JsonPath.read(json, path, filters...); the instance read(...) methods take no path.
            new Target("com.jayway.jsonpath.JsonPath read(*, String, ..)", 1, true, false),
            new Target("com.jayway.jsonpath.ReadContext read(String, ..)", 0, false, false),
            new Target("com.jayway.jsonpath.WriteContext set(String, ..)", 0, false, true),
            new Target("com.jayway.jsonpath.WriteContext put(String, ..)", 0, false, true),
            new Target("com.jayway.jsonpath.WriteContext add(String, ..)", 0, false, true),
            new Target("com.jayway.jsonpath.WriteContext delete(String, ..)", 0, false, true),
            new Target("com.jayway.jsonpath.WriteContext renameKey(String, ..)", 0, false, true),
            new Target("com.jayway.jsonpath.WriteContext map(String, ..)", 0, false, true),
            new Target(
                    "org.springframework.test.web.servlet.result.MockMvcResultMatchers jsonPath(String, ..)",
                    0,
                    true,
                    false));

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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
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
                    if (target.staticOnly && (type == null || !type.hasFlags(org.openrewrite.java.tree.Flag.Static))) {
                        continue;
                    }
                    if (m.getArguments().size() <= target.argument) {
                        continue;
                    }
                    String call = type == null
                            ? m.getSimpleName()
                            : type.getDeclaringType().getClassName() + "." + m.getSimpleName();
                    String sourcePath = getCursor()
                            .firstEnclosingOrThrow(J.CompilationUnit.class)
                            .getSourcePath()
                            .toString();
                    Assessment assessment = assess(m.getArguments().get(target.argument), target.write);
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
        if (!(argument instanceof J.Literal) || !(((J.Literal) argument).getValue() instanceof String)) {
            return new Assessment(
                    null, "NOT_A_LITERAL", "The expression is computed at runtime; check it with JaywayComparison.");
        }
        String expression = (String) ((J.Literal) argument).getValue();
        if (write) {
            return new Assessment(
                    expression,
                    "WRITE_API",
                    "RFC 9535 defines queries only; select the nodes and modify the document with your JSON library.");
        }
        try {
            JsonPath.compile(expression);
        } catch (JsonPathSyntaxException e) {
            return new Assessment(
                    expression, "NOT_RFC_9535", MigrationHints.forInvalidExpression(expression, e.getMessage()));
        }
        if (isDefiniteForJayway(expression)) {
            return new Assessment(
                    expression,
                    "VALID_SINGLE_VALUE",
                    "Valid RFC 9535. Jayway returns a single value here, RFC 9535 a node list: use NodeList.single().");
        }
        return new Assessment(
                expression,
                "VALID",
                "Valid RFC 9535. Results can still differ (slices, type coercion); check with JaywayComparison.");
    }

    private static boolean isDefiniteForJayway(String expression) {
        try {
            return com.jayway.jsonpath.JsonPath.isPathDefinite(expression);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
