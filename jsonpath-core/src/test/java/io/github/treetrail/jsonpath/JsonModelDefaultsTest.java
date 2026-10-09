package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks the compatibility promise of {@link JsonModel}: a model that implements only the abstract
 * methods works through the default methods, and the fast paths of a model agree with its exact ones.
 */
class JsonModelDefaultsTest {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    /** Implements the abstract methods only, by delegating to {@link JavaObjectModel}. */
    private static final JsonModel<Object> MINIMAL = new JsonModel<>() {
        private final JavaObjectModel delegate = JavaObjectModel.INSTANCE;

        @Override
        public JsonKind kind(Object value) {
            return delegate.kind(value);
        }

        @Override
        public Iterable<String> memberNames(Object object) {
            return delegate.memberNames(object);
        }

        @Override
        public boolean hasMember(Object object, String name) {
            return delegate.hasMember(object, name);
        }

        @Override
        public Object member(Object object, String name) {
            return delegate.member(object, name);
        }

        @Override
        public int memberCount(Object object) {
            return delegate.memberCount(object);
        }

        @Override
        public int size(Object array) {
            return delegate.size(array);
        }

        @Override
        public Object element(Object array, int index) {
            return delegate.element(array, index);
        }

        @Override
        public String stringValue(Object value) {
            return delegate.stringValue(value);
        }

        @Override
        public BigDecimal numberValue(Object value) {
            return delegate.numberValue(value);
        }

        @Override
        public boolean booleanValue(Object value) {
            return delegate.booleanValue(value);
        }
    };

    @TestFactory
    Stream<DynamicTest> modelWithOnlyTheAbstractMethodsPassesTheComplianceSuite() {
        return ComplianceSuite.tests(MINIMAL, JsonModelDefaultsTest::parse, JsonModelDefaultsTest::serialize);
    }

    @Test
    void comparesNumbersTheSameWithAndWithoutTheLongFastPath() {
        List<Object> numbers = List.of(
                0,
                -0.0,
                1,
                1L,
                1.0,
                1.5f,
                (short) 2,
                (byte) -3,
                new BigDecimal("2.0"),
                new BigDecimal("10"),
                new BigDecimal("1E+1"),
                10.0,
                9_007_199_254_740_992.0,
                9_007_199_254_740_993L,
                new BigDecimal("9007199254740993"),
                1e18,
                Long.MAX_VALUE,
                Long.MIN_VALUE,
                BigInteger.TWO.pow(63),
                BigInteger.TWO.pow(63).negate(),
                new BigDecimal("123456789012345678"),
                new BigDecimal("1234567890123456789"),
                Double.NaN);
        JsonPath less = JsonPath.compile("$[?@.a < @.b]");
        JsonPath equal = JsonPath.compile("$[?@.a == @.b]");
        List<String> mismatches = new ArrayList<>();
        for (Object a : numbers) {
            for (Object b : numbers) {
                List<Object> doc = List.of(Map.of("a", a, "b", b));
                boolean fastLess = !less.query(doc).isEmpty();
                boolean fastEqual = !equal.query(doc).isEmpty();
                boolean exactLess = !less.query(doc, MINIMAL).isEmpty();
                boolean exactEqual = !equal.query(doc, MINIMAL).isEmpty();
                if (fastLess != exactLess || fastEqual != exactEqual) {
                    mismatches.add(a + " (" + a.getClass().getSimpleName() + ") vs " + b + " ("
                            + b.getClass().getSimpleName() + ")");
                }
            }
        }

        assertThat(mismatches).isEmpty();
    }

    @Test
    void comparesQueryLiteralsWithDocumentNumbersOfEveryType() {
        List<Object> doc = List.of(9, 10, 10L, 10.0, 10.5, new BigDecimal("10.00"), BigInteger.TEN, 11);

        assertThat(JsonPath.compile("$[?@ == 10]").query(doc)).hasSize(5);
        assertThat(JsonPath.compile("$[?@ < 10]").query(doc).values()).containsExactly(9);
        assertThat(JsonPath.compile("$[?@ > 10]").query(doc).values()).containsExactly(10.5, 11);
    }

    private static Object parse(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
