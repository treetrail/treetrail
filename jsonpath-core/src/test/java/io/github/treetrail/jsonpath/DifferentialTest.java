package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Differential test against another RFC 9535 implementation (Python's {@code jsonpath-rfc9535}): random
 * documents and queries, the other implementation's results checked in as
 * {@code src/test/resources/differential/expected.json}. Both must agree on whether a query is valid and,
 * if so, on the selected values and their normalized paths, in order. Differences where the reference is
 * known to be wrong are listed with their reason in {@code differential/known-differences.json}; the test
 * fails for any other difference, and for a listed one that no longer occurs.
 *
 * <p>The system property {@code treetrail.differential.expected} points the test at a freshly generated
 * file instead; see {@code scripts/differential/README.md}.
 */
class DifferentialTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test
    @SuppressWarnings("unchecked")
    void agreesWithTheReferenceImplementation() throws IOException {
        Map<String, Object> expected = load();
        Map<String, String> known = knownDifferences();
        List<String> mismatches = new ArrayList<>();
        java.util.Set<String> knownSeen = new java.util.HashSet<>();
        int queries = 0;
        int valid = 0;
        int nonEmpty = 0;
        int skipped = 0;
        for (Map<String, Object> testCase : (List<Map<String, Object>>) expected.get("cases")) {
            String json = (String) testCase.get("document");
            Object document = JavaObjectModel.parse(json);
            for (Map<String, Object> result : (List<Map<String, Object>>) testCase.get("results")) {
                queries++;
                if (result.containsKey("limit")) {
                    skipped++;
                    continue;
                }
                String query = (String) result.get("query");
                String mismatch = compare(query, document, result);
                if (mismatch != null && known.containsKey(query)) {
                    knownSeen.add(query);
                } else if (mismatch != null) {
                    mismatches.add(mismatch + "\n    query:    " + query + "\n    document: " + json);
                }
                if (Boolean.TRUE.equals(result.get("valid"))) {
                    valid++;
                    if (!((List<?>) result.get("values")).isEmpty()) {
                        nonEmpty++;
                    }
                }
            }
        }
        assertThat(mismatches)
                .as("%d of %d queries differ from %s (seed %s); first ones:%n%s", mismatches.size(), queries,
                        expected.get("reference"), expected.get("seed"),
                        String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size()))))
                .isEmpty();
        // A known difference that no longer occurs means the reference was fixed: drop it from the list.
        if (System.getProperty("treetrail.differential.expected") == null) {
            assertThat(knownSeen).as("known differences that no longer occur").containsExactlyInAnyOrderElementsOf(known.keySet());
        }
        // Guard against a degenerate corpus: most queries valid, many selecting something, few skipped.
        assertThat(valid).isGreaterThan(queries / 2);
        assertThat(nonEmpty).isGreaterThan(valid / 5);
        assertThat(skipped).as("queries beyond the reference's recursion limit").isLessThan(queries / 100);
    }

    @SuppressWarnings("unchecked")
    private static String compare(String query, Object document, Map<String, Object> expected) {
        if (expected.containsKey("error")) {
            return "reference failed: " + expected.get("error");
        }
        NodeList<Object> nodes;
        try {
            nodes = JsonPath.compile(query).query(document);
        } catch (JsonPathSyntaxException e) {
            return Boolean.TRUE.equals(expected.get("valid"))
                    ? "rejected, reference accepts: " + e.getMessage().lines().findFirst().orElse("")
                    : null;
        }
        if (!Boolean.TRUE.equals(expected.get("valid"))) {
            return "accepted, reference rejects";
        }
        List<Object> values = (List<Object>) expected.get("values");
        if (!nodes.paths().equals(expected.get("paths"))) {
            return "paths differ: " + nodes.paths() + " vs reference " + expected.get("paths");
        }
        if (!JavaObjectModel.jsonEquals(nodes.values(), values)) {
            return "values differ: " + nodes.values() + " vs reference " + values;
        }
        return null;
    }

    /** Queries where the reference is known to be wrong, with the reason; see the README of the scripts. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> knownDifferences() throws IOException {
        try (InputStream in = DifferentialTest.class.getResourceAsStream("/differential/known-differences.json")) {
            Map<String, Object> file = MAPPER.readValue(in, Map.class);
            Map<String, String> known = new java.util.LinkedHashMap<>();
            for (Map<String, String> entry : (List<Map<String, String>>) file.get("differences")) {
                known.put(entry.get("query"), entry.get("bug"));
            }
            return known;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() throws IOException {
        String file = System.getProperty("treetrail.differential.expected");
        if (file != null && !file.isEmpty()) {
            return MAPPER.readValue(Files.readString(Path.of(file)), Map.class);
        }
        try (InputStream in = DifferentialTest.class.getResourceAsStream("/differential/expected.json")) {
            return MAPPER.readValue(in, Map.class);
        }
    }
}
