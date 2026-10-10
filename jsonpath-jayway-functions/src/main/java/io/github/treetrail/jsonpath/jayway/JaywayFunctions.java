package io.github.treetrail.jsonpath.jayway;

import io.github.treetrail.jsonpath.FunctionExtension;
import io.github.treetrail.jsonpath.FunctionType;
import io.github.treetrail.jsonpath.FunctionValue;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathCompiler;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The aggregate functions of Jayway JsonPath, {@code min}, {@code max}, {@code sum}, {@code avg} and
 * {@code stddev}, as function extensions for filters. <strong>They are not part of RFC 9535</strong>: queries
 * that use them work only with a compiler that has them, not in other implementations of the standard.
 * They ease a migration from Jayway JsonPath; for new queries, prefer aggregating in Java.
 *
 * <pre>{@code
 * JsonPathCompiler compiler = JaywayFunctions.compiler();
 * JsonPath bigOrders = compiler.compile("$.orders[?sum(@.items[*].price) > 100]");
 * }</pre>
 *
 * <p>Each function takes a node list ({@code @.prices[*]}, {@code $..price}) and uses the numbers among the
 * values, ignoring other values as Jayway does (and numbers that are not finite, such as a Java
 * {@code Double.NaN}). Without any number the result is Nothing, so comparisons
 * with it are false; Jayway throws instead. The results are exact decimals ({@code avg} and {@code stddev} to
 * 16 significant digits); Jayway computes with {@code double}, so its results can differ in the last digits.
 * {@code stddev} is the population standard deviation, as in Jayway.
 */
public final class JaywayFunctions {

    /** The smallest number. */
    public static final FunctionExtension MIN =
            aggregate("min", numbers -> numbers.stream().reduce(BigDecimal::min).orElseThrow());

    /** The largest number. */
    public static final FunctionExtension MAX =
            aggregate("max", numbers -> numbers.stream().reduce(BigDecimal::max).orElseThrow());

    /** The sum of the numbers. */
    public static final FunctionExtension SUM = aggregate("sum", JaywayFunctions::sum);

    /** The arithmetic mean of the numbers. */
    public static final FunctionExtension AVG =
            aggregate("avg", numbers -> sum(numbers).divide(BigDecimal.valueOf(numbers.size()), MathContext.DECIMAL64));

    /** The population standard deviation of the numbers. */
    public static final FunctionExtension STDDEV = aggregate("stddev", numbers -> {
        BigDecimal count = BigDecimal.valueOf(numbers.size());
        BigDecimal mean = sum(numbers).divide(count, MathContext.DECIMAL128);
        BigDecimal squares = BigDecimal.ZERO;
        for (BigDecimal number : numbers) {
            BigDecimal deviation = number.subtract(mean);
            squares = squares.add(deviation.multiply(deviation));
        }
        return squares.divide(count, MathContext.DECIMAL128).sqrt(MathContext.DECIMAL64);
    });

    /** All five functions. */
    public static final List<FunctionExtension> ALL = List.of(MIN, MAX, SUM, AVG, STDDEV);

    private JaywayFunctions() {}

    /** Returns a compiler that knows all five functions, with the default limits. */
    public static JsonPathCompiler compiler() {
        return JsonPath.compiler().withFunctions(ALL.toArray(new FunctionExtension[0]));
    }

    private static FunctionExtension aggregate(String name, Function<List<BigDecimal>, BigDecimal> function) {
        return FunctionExtension.value(name, List.of(FunctionType.NODES), args -> {
            List<BigDecimal> numbers = new ArrayList<>();
            for (FunctionValue value : args.nodes(0)) {
                if (value.kind() == JsonKind.NUMBER) {
                    try {
                        numbers.add(value.number());
                    } catch (IllegalStateException notFinite) {
                        // NaN or an infinity in a model that allows them: not a JSON number, skipped.
                    }
                }
            }
            return numbers.isEmpty() ? FunctionValue.nothing() : FunctionValue.of(function.apply(numbers));
        });
    }

    private static BigDecimal sum(List<BigDecimal> numbers) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal number : numbers) {
            sum = sum.add(number);
        }
        return sum;
    }
}
