package io.github.treetrail.jsonpath.internal;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
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
 * cached automata is bounded: each automaton by {@link #MAX_DFA_BYTES_PER_AUTOMATON} and all of them
 * together by {@link #MAX_DFA_BYTES}. An automaton that reaches its own bound stops growing; when all of
 * them together reach theirs, they are all discarded and rebuilt on demand, as RE2 does. Without a cached
 * state, matching falls back to the plain set-of-states simulation, which is slower but still linear.
 * There is no shared cache of compiled expressions: a query holds its literal patterns and a
 * {@link RegexCache} for the patterns it reads from documents.
 *
 * <p>Expressions that expand into more than {@link #MAX_PROGRAM_SIZE} instructions, for example
 * through large counted repetitions such as {@code (a{1000}){1000}}, are rejected like invalid
 * expressions (RFC 9485, section 8, allows implementations to set such limits).
 *
 * <p>{@link RegexParser} builds a {@link RegexTree}, {@link RegexProgram} compiles it into the automaton,
 * and {@link LazyDfa} runs it, with {@link RegexSimulation} as the fallback. This class holds the memory
 * budget the automata share.
 */
public final class IRegexp {

    /** Maximum number of automaton instructions per expression. */
    static final int MAX_PROGRAM_SIZE = 20_000;

    /** Maximum nesting of groups, to bound recursion on hostile input. */
    static final int MAX_NESTING = 100;

    /** Approximate memory, in bytes, that one automaton (one expression in one mode) may hold. */
    static final long MAX_DFA_BYTES_PER_AUTOMATON = 1L << 20;

    /** Approximate memory, in bytes, that all cached automata may hold together. */
    static final long MAX_DFA_BYTES = 8L << 20;

    /**
     * The compiled expressions still reachable, so that discarding the automata reaches those that queries
     * hold but do not use; guarded by the class lock.
     */
    private static final Map<IRegexp, Boolean> LIVE = new WeakHashMap<>();

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
     * Returns the compiled expression, or empty if {@code regexp} is not a valid I-Regexp. The caller holds
     * it: a query for a literal pattern, a {@link RegexCache} for patterns from documents. Its automata count
     * against the shared memory budget.
     */
    public static Optional<IRegexp> compile(String regexp) {
        IRegexp compiled;
        try {
            compiled = new IRegexp(RegexProgram.compile(new RegexParser(regexp).parse()));
        } catch (InvalidRegexp e) {
            return Optional.empty();
        }
        synchronized (IRegexp.class) {
            LIVE.put(compiled, Boolean.TRUE);
        }
        return Optional.of(compiled);
    }

    /**
     * Discards all cached automata unless that already happened since {@code generation} was read.
     * The compiled expressions drop their automata so that the memory can be reclaimed; an automaton in
     * use by a running match stops growing and is collected when that match ends.
     */
    private static synchronized void discardAutomata(int generation) {
        if (dfaGeneration == generation) {
            dfaGeneration = generation + 1;
            DFA_BYTES.set(0);
            for (IRegexp live : LIVE.keySet()) {
                live.dropAutomata();
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

    /**
     * Drops the automata of an expression that is no longer used, returning their memory to the shared
     * budget. The amount is approximate if a match is still running on them.
     */
    void retire() {
        int generation = dfaGeneration;
        for (LazyDfa dfa : new LazyDfa[] {matchDfa, searchDfa}) {
            if (dfa != null && dfa.generation() == generation) {
                releaseShared(dfa.bytes(), generation);
            }
        }
        dropAutomata();
    }

    /** Estimated bytes held by the cached automata; for tests. */
    static long cachedDfaBytes() {
        return DFA_BYTES.get();
    }

    /** Number of automaton instructions; for {@link RegexCache}. */
    int programSize() {
        return program.size();
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

    /** Whether the whole input matches ({@code match()}). */
    public boolean matches(String input) {
        return input.isEmpty()
                ? RegexSimulation.run(program, input, false)
                : automaton(false).run(input);
    }

    /** Whether some substring of the input matches ({@code search()}). */
    public boolean find(String input) {
        return input.isEmpty()
                ? RegexSimulation.run(program, input, true)
                : automaton(true).run(input);
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
