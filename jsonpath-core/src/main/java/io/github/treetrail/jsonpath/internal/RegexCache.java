package io.github.treetrail.jsonpath.internal;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The compiled patterns that one {@code match()} or {@code search()} call of a query read from documents,
 * as in {@code match(@.value, @.pattern)}. Each call has its own cache, so patterns from one query's
 * documents cannot evict those of another query, and a cache holds at most {@link #MAX_ENTRIES} expressions
 * with at most {@link #MAX_INSTRUCTIONS} instructions together. When it is full it is cleared, as patterns
 * usually repeat and a full cache means unusual input.
 *
 * <p>Queries are evaluated concurrently, so reads are lock-free and changes hold the cache's lock.
 */
final class RegexCache {

    /** Maximum number of expressions per call. */
    static final int MAX_ENTRIES = 64;

    /** Maximum number of automaton instructions of the expressions per call. */
    static final long MAX_INSTRUCTIONS = 2L * IRegexp.MAX_PROGRAM_SIZE;

    private final Map<String, Optional<IRegexp>> entries = new ConcurrentHashMap<>();

    /** Instructions of the expressions in {@link #entries}; guarded by the lock. */
    private long instructions;

    /** Returns the compiled expression, or empty if {@code regexp} is not a valid I-Regexp. */
    Optional<IRegexp> get(String regexp) {
        Optional<IRegexp> cached = entries.get(regexp);
        if (cached != null) {
            return cached;
        }
        Optional<IRegexp> compiled = IRegexp.compile(regexp);
        long weight = compiled.map(r -> (long) r.programSize()).orElse(1L);
        synchronized (this) {
            if (entries.size() >= MAX_ENTRIES || instructions + weight > MAX_INSTRUCTIONS) {
                for (Optional<IRegexp> evicted : entries.values()) {
                    evicted.ifPresent(IRegexp::retire);
                }
                entries.clear();
                instructions = 0;
            }
            Optional<IRegexp> concurrent = entries.putIfAbsent(regexp, compiled);
            if (concurrent != null) {
                return concurrent;
            }
            instructions += weight;
        }
        return compiled;
    }

    /** Number of cached expressions; for tests. */
    int size() {
        return entries.size();
    }

    /** Instructions of the cached expressions; for tests. */
    synchronized long instructions() {
        return instructions;
    }
}
