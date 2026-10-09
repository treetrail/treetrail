package io.github.treetrail.jsonpath.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.treetrail.jsonpath.JsonPath;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins down observed differences between Jayway JsonPath 3.0.0 (default configuration, parsing the
 * JSON itself) and RFC 9535. Each case comes from the Compliance Test Suite. If Jayway changes its
 * behaviour, these tests fail and the migration hints must be revisited.
 */
class JaywayBehaviourTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIGITS = "[0,1,2,3,4,5,6,7,8,9]";

    @Test
    void jaywayIgnoresTheSliceStep() {
        assertThat(jayway(DIGITS, "$[1:6:2]")).isEqualTo(List.of(1, 2, 3, 4, 5));
        assertThat(rfc(DIGITS, "$[1:6:2]")).isEqualTo(List.of(1, 3, 5));
    }

    @Test
    void jaywayHandlesNegativeSliceStartDifferently() {
        assertThat(rfc(DIGITS, "$[-5:7]")).isEqualTo(List.of(5, 6));
        assertThat(jayway(DIGITS, "$[-5:7]")).isNotEqualTo(List.of(5, 6));
    }

    @Test
    void jaywayComparesNumbersAndNumericStringsAsEqual() {
        String json =
                "[{\"a\":1,\"d\":\"e\"},{\"a\":\"c\",\"d\":\"f\"},{\"a\":2,\"d\":\"f\"},{\"a\":\"1\",\"d\":\"f\"}]";

        assertThat(jayway(json, "$[?(@.a==1)].d")).isEqualTo(List.of("e", "f"));
        assertThat(rfc(json, "$[?@.a==1].d")).isEqualTo(List.of("e"));
    }

    @Test
    void jaywayReturnsOneObjectForSeveralMemberNames() {
        String json = "{\"a\":\"ab\",\"b\":\"bc\"}";

        assertThat(jaywayRaw(json, "$['a','b']")).isInstanceOf(java.util.Map.class);
        assertThat(rfc(json, "$['a','b']")).isEqualTo(List.of("ab", "bc"));
    }

    @Test
    void jaywaySilentlyMisreadsLineBreaksAndTabsAroundOperators() {
        String json = "[{\"a\":1},{\"b\":2},{\"c\":3}]";

        assertThat(jayway(json, "$[?(@.a || @.b)]")).hasSize(2);
        // Exactly as in the Compliance Test Suite: no error, just an empty result.
        assertThat(jayway(json, "$[?(@.a\n||@.b)]")).isEmpty();
        assertThat(jayway(json, "$[?(@.a\t||@.b)]")).isEmpty();
        assertThat(rfc(json, "$[?@.a\n||@.b]")).hasSize(2);
    }

    @Test
    void jaywayTreatsAWildcardExistenceTestAsTrueForEmptyContainers() {
        String json = "[1,[],[2],{},{\"a\":3}]";

        assertThat(jayway(json, "$[?(@.*)]")).hasSize(5);
        assertThat(rfc(json, "$[?@.*]")).hasSize(2);
    }

    private static List<Object> rfc(String json, String path) {
        return JsonPath.compile(path).query(parse(json)).values();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> jayway(String json, String path) {
        return (List<Object>) jaywayRaw(json, path);
    }

    private static Object jaywayRaw(String json, String path) {
        return com.jayway.jsonpath.JsonPath.read(json, path);
    }

    private static Object parse(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
