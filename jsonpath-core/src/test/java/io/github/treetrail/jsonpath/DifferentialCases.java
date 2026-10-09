package io.github.treetrail.jsonpath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes random documents and queries for the differential test against another RFC 9535
 * implementation: {@code [{"document": "<JSON text>", "queries": ["$...", ...]}, ...]}.
 * Run through {@code ./gradlew :jsonpath-core:generateDifferentialCases}; see
 * {@code scripts/differential/README.md}.
 */
public final class DifferentialCases {

    private DifferentialCases() {}

    /** Arguments: output file, seed, number of documents, queries per document. */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        long seed = Long.parseLong(args[1]);
        int documents = Integer.parseInt(args[2]);
        int queriesPerDocument = Integer.parseInt(args[3]);
        RandomQueries random = new RandomQueries(seed);
        List<Object> cases = new ArrayList<>();
        for (int d = 0; d < documents; d++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("document", TestJson.write(random.document()));
            List<Object> queries = new ArrayList<>();
            for (int q = 0; q < queriesPerDocument; q++) {
                queries.add(random.query());
            }
            entry.put("queries", queries);
            cases.add(entry);
        }
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("seed", seed);
        file.put("cases", cases);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, TestJson.write(file), StandardCharsets.UTF_8);
    }
}
