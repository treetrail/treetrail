package io.github.treetrail.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Compiled queries are shared between threads, and the regex automata they use are built lazily and
 * published between threads without locks. Many threads run the same compiled queries over the same
 * documents and must get exactly the results of a single-threaded run.
 */
class ConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ROUNDS = 5;

    /**
     * {@code %s} sits inside each regular expression; it is empty for the single-threaded run and a run of
     * empty groups {@code ()} for the threads. That changes nothing about what matches, but makes each
     * round's expressions new cache entries, so their automata are built while the threads race.
     */
    private static final String[] QUERIES = {
        "$..[?match(@, 'a.*%s')]",
        "$..[?search(@, '[b-c]+%s')]",
        "$..[?match(@, '\\\\p{L}*😀?%s\\\\p{L}*')]",
        "$..[?search(@, 'é%s|😀') || @.a == 1]",
        "$..[?length(@) > 1 && !@.b]",
        "$..*[1:]",
    };

    @Test
    void sharedQueriesGiveTheSameResultsAsOneThread() throws Exception {
        RandomQueries random = new RandomQueries(16);
        List<Object> documents = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            documents.add(random.document());
        }
        List<List<String>> expected = new ArrayList<>();
        for (String query : QUERIES) {
            JsonPath baseline = JsonPath.compile(String.format(query, ""));
            int hits = 0;
            for (Object document : documents) {
                List<String> paths = baseline.query(document).paths();
                expected.add(paths);
                hits += paths.isEmpty() ? 0 : 1;
            }
            assertThat(hits).as("documents where %s selects something", query).isGreaterThan(10);
        }

        for (int round = 0; round < ROUNDS; round++) {
            List<JsonPath> shared = new ArrayList<>();
            for (String query : QUERIES) {
                shared.add(JsonPath.compile(String.format(query, "()".repeat(round + 1))));
            }
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int offset = t;
                results.add(pool.submit(() -> {
                    start.await();
                    int mismatches = 0;
                    for (int i = 0; i < documents.size(); i++) {
                        int d = (i + offset * 17) % documents.size();
                        for (int q = 0; q < QUERIES.length; q++) {
                            List<String> paths =
                                    shared.get(q).query(documents.get(d)).paths();
                            if (!paths.equals(expected.get(q * documents.size() + d))) {
                                mismatches++;
                            }
                        }
                    }
                    return mismatches;
                }));
            }
            start.countDown();
            int mismatches = 0;
            for (Future<Integer> result : results) {
                mismatches += result.get(60, TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(mismatches).as("round %d", round).isZero();
        }
    }
}
