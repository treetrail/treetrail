package io.github.treetrail.jsonpath.internal;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * I-Regexp (RFC 9485), the interoperable regular expression format used by the {@code match()}
 * and {@code search()} functions.
 *
 * <p>An expression is parsed strictly against the I-Regexp grammar and compiled into a
 * nondeterministic automaton that is simulated without backtracking (Thompson's construction).
 * Matching therefore takes time linear in the length of the input for a given expression, so
 * expressions from untrusted sources cannot cause catastrophic backtracking.
 *
 * <p>For speed, sets of automaton states are turned into deterministic states on first use and
 * their transitions are cached (a lazy DFA, as in RE2), so a cached step costs one array lookup.
 *
 * <p>Expressions may come from documents ({@code match(@.value, @.pattern)}), so the memory held by
 * cached automata is bounded: each automaton by {@link #MAX_DFA_BYTES_PER_AUTOMATON}, all of them
 * together by {@link #MAX_DFA_BYTES}, and the compiled expressions by {@link #MAX_CACHED_INSTRUCTIONS}.
 * An automaton that reaches its own bound stops growing; when all of them together reach theirs, they
 * are all discarded and rebuilt on demand, as RE2 does. Without a cached state, matching falls back to
 * the plain set-of-states simulation, which is slower but still linear.
 *
 * <p>Expressions that expand into more than {@link #MAX_PROGRAM_SIZE} instructions, for example
 * through large counted repetitions such as {@code (a{1000}){1000}}, are rejected like invalid
 * expressions (RFC 9485, section 8, allows implementations to set such limits).
 *
 * <p>{@link RegexParser} builds a {@link RegexTree}, {@link RegexProgram} compiles it into the automaton,
 * and {@link LazyDfa} runs it, with {@link RegexSimulation} as the fallback. This class holds the caches
 * and the memory budget they share.
 */
public final class IRegexp {

    /** Maximum number of automaton instructions per expression. */
    static final int MAX_PROGRAM_SIZE = 20_000;

    /** Maximum nesting of groups, to bound recursion on hostile input. */
    static final int MAX_NESTING = 100;

    private static final int CACHE_SIZE = 256;

    /** Approximate memory, in bytes, that one automaton (one expression in one mode) may hold. */
    static final long MAX_DFA_BYTES_PER_AUTOMATON = 1L << 20;

    /** Approximate memory, in bytes, that all cached automata may hold together. */
    static final long MAX_DFA_BYTES = 8L << 20;

    /** Maximum number of instructions of all cached compiled expressions together. */
    static final long MAX_CACHED_INSTRUCTIONS = 100_000;

    /** Compiled expressions; reads are lock-free because filters call match() once per node. */
    private static final Map<String, Optional<IRegexp>> CACHE = new ConcurrentHashMap<>();

    /** Instructions of the expressions in {@link #CACHE}; changed only while holding the class lock. */
    private static final AtomicLong CACHED_INSTRUCTIONS = new AtomicLong();

    /** Estimated bytes held by the automata of the current generation. */
    private static final AtomicLong DFA_BYTES = new AtomicLong();

    /** Generation of the automata; incremented to discard all of them at once. */
    private static volatile int dfaGeneration;

    private final RegexProgram program;
    private volatile @Nullable LazyDfa matchDfa;
    private volatile @Nullable LazyDfa searchDfa;

    private IRegexp(RegexProgram program) {
        this.program = program;
    }

    /**
     * Compiles an expression for one query, without the shared cache: the query holds it, for example a
     * literal pattern in {@code match(@.a, 'x.*')}. Its automata still count against the shared memory budget.
     */
    public static Optional<IRegexp> compileForQuery(String regexp) {
        return doCompile(regexp);
    }

    /**
     * Returns the compiled expression, or empty if {@code regexp} is not a valid I-Regexp. Compiled
     * expressions are cached, for patterns that come from documents ({@code match(@.a, @.pattern)}).
     */
    public static Optional<IRegexp> compile(String regexp) {
        Optional<IRegexp> cached = CACHE.get(regexp);
        if (cached != null) {
            return cached;
        }
        Optional<IRegexp> compiled = doCompile(regexp);
        long weight = compiled.map(r -> (long) r.program.size()).orElse(1L);
        synchronized (IRegexp.class) {
            if (CACHE.size() >= CACHE_SIZE || CACHED_INSTRUCTIONS.get() + weight > MAX_CACHED_INSTRUCTIONS) {
                // Expressions usually come from a handful of queries; a full cache means unusual input.
                // The automata of the evicted expressions are no longer counted, so discard all of them.
                CACHE.clear();
                CACHED_INSTRUCTIONS.set(0);
                discardAutomata(dfaGeneration);
            }
            if (CACHE.putIfAbsent(regexp, compiled) == null) {
                CACHED_INSTRUCTIONS.addAndGet(weight);
            }
        }
        return compiled;
    }

    /**
     * Discards all cached automata unless that already happened since {@code generation} was read.
     * The cached expressions drop their automata so that the memory can be reclaimed; an automaton in
     * use by a running match stops growing and is collected when that match ends.
     */
    private static synchronized void discardAutomata(int generation) {
        if (dfaGeneration == generation) {
            dfaGeneration = generation + 1;
            DFA_BYTES.set(0);
            for (Optional<IRegexp> cached : CACHE.values()) {
                cached.ifPresent(IRegexp::dropAutomata);
            }
        }
    }

    /** Reserves memory for all automata of a generation; if they are full, discards them all and returns false. */
    static boolean reserveShared(long cost, int generation) {
        if (DFA_BYTES.addAndGet(cost) > MAX_DFA_BYTES) {
            DFA_BYTES.addAndGet(-cost);
            discardAutomata(generation);
            return false;
        }
        return true;
    }

    /** Returns memory reserved by {@link #reserveShared} unless its generation has been discarded since. */
    static void releaseShared(long cost, int generation) {
        if (generation == dfaGeneration) {
            DFA_BYTES.addAndGet(-cost);
        }
    }

    private void dropAutomata() {
        matchDfa = null;
        searchDfa = null;
    }

    /** Estimated bytes held by the cached automata; for tests. */
    static long cachedDfaBytes() {
        return DFA_BYTES.get();
    }

    /** Instructions of the cached compiled expressions; for tests. */
    static long cachedInstructions() {
        return CACHED_INSTRUCTIONS.get();
    }

    /** Generation of the automata, incremented whenever all of them are discarded; for tests. */
    static int dfaGeneration() {
        return dfaGeneration;
    }

    /** Estimated bytes held by this expression's automaton for one mode; for tests. */
    long automatonBytes(boolean search) {
        LazyDfa dfa = search ? searchDfa : matchDfa;
        return dfa == null ? 0 : dfa.bytes();
    }

    private static Optional<IRegexp> doCompile(String regexp) {
        try {
            return Optional.of(new IRegexp(RegexProgram.compile(new RegexParser(regexp).parse())));
        } catch (InvalidRegexp e) {
            return Optional.empty();
        }
    }

    /** Whether the whole input matches ({@code match()}). */
    public boolean matches(String input) {
        Boolean result = input.isEmpty() ? null : automaton(false).run(input);
        return result != null ? result : RegexSimulation.run(program, input, false);
    }

    /** Whether some substring of the input matches ({@code search()}). */
    public boolean find(String input) {
        Boolean result = input.isEmpty() ? null : automaton(true).run(input);
        return result != null ? result : RegexSimulation.run(program, input, true);
    }

    /** Returns the automaton of the current generation for a mode, creating it if necessary. */
    private LazyDfa automaton(boolean search) {
        int generation = dfaGeneration;
        LazyDfa dfa = search ? searchDfa : matchDfa;
        if (dfa != null && dfa.generation() == generation) {
            return dfa;
        }
        synchronized (this) {
            dfa = search ? searchDfa : matchDfa;
            if (dfa == null || dfa.generation() != generation) {
                dfa = new LazyDfa(program, search, generation);
                if (search) {
                    searchDfa = dfa;
                } else {
                    matchDfa = dfa;
                }
            }
            return dfa;
        }
    }
}
