package io.github.treetrail.jsonpath.migration;

import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import io.github.treetrail.jsonpath.migration.Comparison.Outcome;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Runs JSONPath expressions with Jayway JsonPath and with this library and reports every difference.
 *
 * <p>Use it in a test with the expressions and sample documents of your application, before switching:
 *
 * <pre>{@code
 * MigrationReport report = JaywayComparison.withDefaults().compareAll(expressions, document);
 * System.out.println(report);
 * assertThat(report.differences()).isEmpty();
 * }</pre>
 *
 * <p>Documents are plain Java objects ({@code Map}, {@code List}, {@code String}, {@code Number},
 * {@code Boolean}, {@code null}), as produced by {@code objectMapper.readValue(json, Object.class)}.
 * Pass your application's Jayway {@link Configuration} so that options such as
 * {@link Option#DEFAULT_PATH_LEAF_TO_NULL} are taken into account.
 */
public final class JaywayComparison {

    private final Configuration jayway;

    private JaywayComparison(Configuration jayway) {
        if (jayway.getOptions().contains(Option.AS_PATH_LIST)) {
            throw new IllegalArgumentException("Option.AS_PATH_LIST is not supported; compare values instead");
        }
        this.jayway = jayway;
    }

    /** Compares against Jayway's default configuration. */
    public static JaywayComparison withDefaults() {
        return new JaywayComparison(Configuration.defaultConfiguration());
    }

    /** Compares against the given Jayway configuration, typically the one your application uses. */
    public static JaywayComparison with(Configuration configuration) {
        return new JaywayComparison(configuration);
    }

    /** Compares several expressions against the same document. */
    public MigrationReport compareAll(Collection<String> expressions, Object document) {
        List<Comparison> comparisons = new ArrayList<>(expressions.size());
        for (String expression : expressions) {
            comparisons.add(compare(expression, document));
        }
        return new MigrationReport(comparisons);
    }

    /**
     * Compares one expression against a JSON text. The text is parsed once with Jayway's configured
     * JSON provider, as Jayway would do, and both libraries query the result. The provider must
     * produce plain Java objects (the default json-smart provider does).
     */
    public Comparison compareJson(String expression, String json) {
        Object document = jayway.jsonProvider().parse(json);
        if (document != null && !(document instanceof Map) && !(document instanceof List)
                && !(document instanceof String) && !(document instanceof Number) && !(document instanceof Boolean)) {
            throw new IllegalArgumentException("Jayway's JSON provider produced " + document.getClass().getName()
                    + "; use a provider that produces Map/List, such as the default json-smart provider");
        }
        return compare(expression, document);
    }

    /** Compares several expressions against the same JSON text; see {@link #compareJson(String, String)}. */
    public MigrationReport compareAllJson(Collection<String> expressions, String json) {
        List<Comparison> comparisons = new ArrayList<>(expressions.size());
        for (String expression : expressions) {
            comparisons.add(compareJson(expression, json));
        }
        return new MigrationReport(comparisons);
    }

    /** Compares one expression against a document. */
    public Comparison compare(String expression, Object document) {
        List<Object> rfc;
        String rfcError = null;
        try {
            rfc = new ArrayList<>(JsonPath.compile(expression).query(document).values());
        } catch (JsonPathSyntaxException e) {
            rfc = null;
            rfcError = e.getMessage();
        }

        com.jayway.jsonpath.JsonPath compiled;
        try {
            compiled = com.jayway.jsonpath.JsonPath.compile(expression);
        } catch (InvalidPathException | IllegalArgumentException e) {
            if (rfc == null) {
                return new Comparison(expression, Outcome.BOTH_REJECT, null, null, false, rfcError);
            }
            return new Comparison(expression, Outcome.ONLY_RFC_ACCEPTS, rfc, null, false,
                    "Jayway: " + e.getMessage());
        }

        List<Object> jaywayValues;
        boolean single = false;
        try {
            Object result = compiled.read(document, jayway);
            boolean returnsList = !compiled.isDefinite() || jayway.getOptions().contains(Option.ALWAYS_RETURN_LIST);
            if (returnsList && result instanceof List) {
                jaywayValues = new ArrayList<>((List<?>) result);
            } else {
                jaywayValues = new ArrayList<>();
                jaywayValues.add(result);
                single = true;
            }
        } catch (PathNotFoundException e) {
            jaywayValues = new ArrayList<>();
        } catch (RuntimeException e) {
            if (rfc == null) {
                return new Comparison(expression, Outcome.BOTH_REJECT, null, null, false, rfcError);
            }
            return new Comparison(expression, Outcome.JAYWAY_FAILS_AT_RUNTIME, rfc, null, false,
                    "Jayway: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        if (rfc == null) {
            return new Comparison(expression, Outcome.ONLY_JAYWAY_ACCEPTS, null, jaywayValues, single,
                    MigrationHints.forInvalidExpression(expression, rfcError));
        }
        Outcome outcome;
        if (jsonEquals(rfc, jaywayValues)) {
            outcome = Outcome.SAME;
        } else if (sameValuesInAnyOrder(rfc, jaywayValues)) {
            outcome = Outcome.SAME_VALUES_DIFFERENT_ORDER;
        } else {
            outcome = Outcome.DIFFERENT_VALUES;
        }
        return new Comparison(expression, outcome, rfc, jaywayValues, single, null);
    }

    private static boolean sameValuesInAnyOrder(List<Object> a, List<Object> b) {
        if (a.size() != b.size()) {
            return false;
        }
        List<Object> remaining = new ArrayList<>(b);
        for (Object value : a) {
            boolean found = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (jsonEquals(value, remaining.get(i))) {
                    remaining.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /** JSON value equality: numbers by value, objects by members, arrays by elements. */
    static boolean jsonEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            try {
                return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
            } catch (NumberFormatException e) {
                return a.equals(b);
            }
        }
        if (a instanceof List && b instanceof List) {
            List<?> la = (List<?>) a;
            List<?> lb = (List<?>) b;
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!jsonEquals(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> ma = (Map<?, ?>) a;
            Map<?, ?> mb = (Map<?, ?>) b;
            if (!ma.keySet().equals(mb.keySet())) {
                return false;
            }
            for (Object key : ma.keySet()) {
                if (!jsonEquals(ma.get(key), mb.get(key))) {
                    return false;
                }
            }
            return true;
        }
        return a == null ? b == null : a.equals(b);
    }
}
