package io.github.treetrail.jsonpath.benchmarks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic test documents as plain Java objects, queried by both libraries.
 */
final class Documents {

    private static final String[] AUTHORS = {"Nigel Rees", "Evelyn Waugh", "Herman Melville", "J. R. R. Tolkien"};
    private static final String[] CATEGORIES = {"reference", "fiction"};

    private Documents() {
    }

    /** The classic bookstore, with {@code books} books. */
    static Map<String, Object> store(int books) {
        Random random = new Random(9535);
        List<Object> book = new ArrayList<>(books);
        for (int i = 0; i < books; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("category", CATEGORIES[random.nextInt(CATEGORIES.length)]);
            b.put("author", AUTHORS[random.nextInt(AUTHORS.length)]);
            b.put("title", "Book " + i);
            if (random.nextInt(3) == 0) {
                b.put("isbn", "0-" + random.nextInt(1000) + "-" + random.nextInt(100000) + "-" + random.nextInt(10));
            }
            b.put("price", Math.round(random.nextDouble() * 3000) / 100.0);
            b.put("tags", List.of("t" + random.nextInt(5), "t" + random.nextInt(5)));
            book.add(b);
        }
        Map<String, Object> bicycle = new LinkedHashMap<>();
        bicycle.put("color", "red");
        bicycle.put("price", 399);
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("book", book);
        store.put("bicycle", bicycle);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("store", store);
        return root;
    }
}
