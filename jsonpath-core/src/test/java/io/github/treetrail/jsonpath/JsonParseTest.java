package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JsonParseTest {

    private static final ObjectMapper JACKSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test
    void parsesEveryComplianceSuiteDocumentLikeJackson() throws JsonProcessingException {
        int documents = 0;
        for (Map<String, Object> test : ComplianceSuite.cases()) {
            if (!test.containsKey("document")) {
                continue;
            }
            String json = JACKSON.writeValueAsString(test.get("document"));
            assertThat(jsonEquals(JavaObjectModel.parse(json), JACKSON.readValue(json, Object.class)))
                    .as("document of %s", test.get("name"))
                    .isTrue();
            documents++;
        }
        assertThat(documents).as("documents in the suite").isGreaterThan(400);
    }

    @Test
    void readsRandomDocumentsWrittenByJackson() throws JsonProcessingException {
        Random random = new Random(8259);
        for (int i = 0; i < 2_000; i++) {
            Object document = randomValue(random, 0);
            String json = JACKSON.writeValueAsString(document);
            assertThat(jsonEquals(JavaObjectModel.parse(json), document)).as(json).isTrue();
        }
    }

    @Test
    void choosesTheSmallestIntegerTypeAndExactDecimals() {
        assertThat(JavaObjectModel.parse("1")).isEqualTo(1);
        assertThat(JavaObjectModel.parse("-2147483649")).isEqualTo(-2_147_483_649L);
        assertThat(JavaObjectModel.parse("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        assertThat(JavaObjectModel.parse("9223372036854775808")).isEqualTo(new BigInteger("9223372036854775808"));
        assertThat(JavaObjectModel.parse("8.95")).isEqualTo(new BigDecimal("8.95"));
        assertThat(JavaObjectModel.parse("1e400")).isEqualTo(new BigDecimal("1e400"));
        assertThat(JavaObjectModel.parse("-0")).isEqualTo(0);
    }

    @Test
    void keepsMembersInDocumentOrderAndParsesAllValueKinds() {
        Object document = JavaObjectModel.parse(
                " {\"b\": [true, false, null], \"a\": \"\\u00e9\\ud83d\\ude00\\n\\/\", \"c\": {}}\r\n");

        assertThat(document).isInstanceOf(LinkedHashMap.class);
        assertThat(new ArrayList<Object>(((Map<?, ?>) document).keySet())).containsExactly("b", "a", "c");
        assertThat(((Map<?, ?>) document).get("b")).isEqualTo(java.util.Arrays.asList(true, false, null));
        assertThat(((Map<?, ?>) document).get("a")).isEqualTo("é😀\n/");
        assertThat(JavaObjectModel.parse("\"top-level string\"")).isEqualTo("top-level string");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "''                         | Expected a value                  | 0",
        "'[1,]'                     | Expected a value                  | 3",
        "'{\"a\":1,}'               | Expected a member name            | 7",
        "'[01]'                     | Expected ',' or ']'               | 2",
        "'[1.]'                     | Expected a digit after '.'        | 3",
        "'[1e]'                     | Expected a digit in the exponent  | 3",
        "'\"abc'                    | Unterminated string               | 0",
        "'\"a\\x\"'                 | Invalid escape                    | 2",
        "'\"\\u12g4\"'              | Expected four hex digits          | 3",
        "'\"\\ud800\"'              | Unpaired surrogate                | 7",
        "'\"\\udc00\"'              | Unpaired surrogate                | 7",
        "'{\"a\":1,\"a\":2}'        | Duplicate member name             | 7",
        "'[1] [2]'                  | Unexpected text after the JSON value | 4",
        "'tru'                      | Expected a value                  | 0",
        "'1e9999999999'             | Number out of range               | 0",
        "'[NaN]'                    | Expected a value                  | 1",
    })
    void rejectsInvalidJsonWithReasonAndPosition(String json, String reason, int position) {
        assertThatThrownBy(() -> JavaObjectModel.parse(json))
                .isInstanceOfSatisfying(InvalidJsonException.class, e -> {
                    assertThat(e.reason()).startsWith(reason);
                    assertThat(e.position()).isEqualTo(position);
                });
    }

    @Test
    void rejectsControlCharactersAndNonAsciiHexDigits() {
        assertThatThrownBy(() -> JavaObjectModel.parse("\"a\tb\"")).isInstanceOf(InvalidJsonException.class);
        assertThatThrownBy(() -> JavaObjectModel.parse("\"\\u\u0663\u0663\u0663\u0663\"")).isInstanceOf(InvalidJsonException.class);
        assertThatThrownBy(() -> JavaObjectModel.parse("\"\uD800\"")).isInstanceOf(InvalidJsonException.class);
        assertThatThrownBy(() -> JavaObjectModel.parse("\f1")).isInstanceOf(InvalidJsonException.class);
    }

    @Test
    void limitsNestingTo1000Levels() {
        assertThat(JavaObjectModel.parse("[".repeat(1_000) + "]".repeat(1_000))).isInstanceOf(List.class);
        assertThatThrownBy(() -> JavaObjectModel.parse("[".repeat(1_001) + "]".repeat(1_001)))
                .isInstanceOf(InvalidJsonException.class)
                .hasMessageContaining("1000 levels");
    }

    @Test
    void queriesJsonText() {
        NodeList<Object> titles = JsonPath.compile("$.store.book[?@.price < 10].title")
                .queryJson("{\"store\":{\"book\":[{\"title\":\"A\",\"price\":8.95},{\"title\":\"B\",\"price\":12}]}}");

        assertThat(titles.values()).containsExactly("A");
    }

    private static Object randomValue(Random random, int depth) {
        int kind = random.nextInt(depth > 4 ? 6 : 8);
        switch (kind) {
            case 0:
                return null;
            case 1:
                return random.nextBoolean();
            case 2:
                return random.nextInt();
            case 3:
                return random.nextLong();
            case 4:
                return new BigDecimal(BigInteger.valueOf(random.nextLong()), random.nextInt(40) - 20);
            case 5:
                StringBuilder sb = new StringBuilder();
                for (int i = random.nextInt(8); i > 0; i--) {
                    sb.appendCodePoint(random.nextInt(4) == 0 ? random.nextInt(0x20) : 0x20 + random.nextInt(0x1F000));
                }
                String s = sb.toString();
                // Drop unpaired surrogates from the random code points; JSON writers produce none either.
                return s.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF) ? "s" : s;
            case 6:
                List<Object> list = new ArrayList<>();
                for (int i = random.nextInt(5); i > 0; i--) {
                    list.add(randomValue(random, depth + 1));
                }
                return list;
            default:
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = random.nextInt(5); i > 0; i--) {
                    map.put("k" + random.nextInt(10), randomValue(random, depth + 1));
                }
                return map;
        }
    }

    private static boolean jsonEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
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

    @Test
    void comparesJavaValuesAsJson() {
        assertThat(JavaObjectModel.jsonEquals(10, 10L)).isTrue();
        assertThat(JavaObjectModel.jsonEquals(8.95, new BigDecimal("8.950"))).isTrue();
        assertThat(JavaObjectModel.jsonEquals(Map.of("a", 1, "b", List.of(1, 2)), JavaObjectModel.parse("{\"b\":[1,2],\"a\":1.0}")))
                .isTrue();
        assertThat(JavaObjectModel.jsonEquals(List.of(1, 2), List.of(2, 1))).isFalse();
        assertThat(JavaObjectModel.jsonEquals("1", 1)).isFalse();
        assertThat(JavaObjectModel.jsonEquals(null, null)).isTrue();
    }
}
