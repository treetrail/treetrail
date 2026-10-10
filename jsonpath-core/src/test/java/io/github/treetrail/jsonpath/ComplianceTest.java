package io.github.treetrail.jsonpath;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.treetrail.jsonpath.testkit.JsonModelTestKit;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the JSONPath Compliance Test Suite against plain Java objects ({@link JavaObjectModel}): once as
 * the README recommends, with Jackson's defaults (floating-point numbers become {@code Double}), and once
 * with {@code BigDecimal} for exact decimals.
 */
class ComplianceTest {

    private static final ObjectMapper DEFAULTS = new ObjectMapper();
    private static final ObjectMapper DECIMALS =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @TestFactory
    Stream<DynamicTest> complianceTestSuiteWithJacksonDefaults() {
        return JsonModelTestKit.complianceTests(JavaObjectModel.INSTANCE, json -> parse(DEFAULTS, json));
    }

    @TestFactory
    Stream<DynamicTest> complianceTestSuiteWithTheBuiltInParser() {
        return JsonModelTestKit.complianceTests(JavaObjectModel.INSTANCE, JavaObjectModel::parse);
    }

    @TestFactory
    Stream<DynamicTest> modelContract() {
        return JsonModelTestKit.contractTests(JavaObjectModel.INSTANCE, JavaObjectModel::parse);
    }

    @TestFactory
    Stream<DynamicTest> complianceTestSuiteWithBigDecimals() {
        return JsonModelTestKit.complianceTests(JavaObjectModel.INSTANCE, json -> parse(DECIMALS, json));
    }

    private static Object parse(ObjectMapper mapper, String json) {
        try {
            return mapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
