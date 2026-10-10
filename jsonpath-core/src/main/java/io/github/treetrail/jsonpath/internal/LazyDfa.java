package io.github.treetrail.jsonpath.internal;

import static io.github.treetrail.jsonpath.internal.RegexProgram.BEGIN;
import static io.github.treetrail.jsonpath.internal.RegexProgram.CHAR;
import static io.github.treetrail.jsonpath.internal.RegexProgram.END;
import static io.github.treetrail.jsonpath.internal.RegexProgram.JUMP;
import static io.github.treetrail.jsonpath.internal.RegexProgram.MATCH;
import static io.github.treetrail.jsonpath.internal.RegexProgram.SPLIT;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Lazily built deterministic automaton for non-empty inputs. Each state is a set of CHAR
 * instructions waiting for the next code point, plus END instructions that only pass at the end
 * of the input, plus whether MATCH was reached. States and transitions are shared between threads:
 * states are immutable apart from their transition caches, whose entries are published racily but
 * safely (all fields of a state are final).
 */
final class LazyDfa {

    /** Maximum number of cached deterministic states per automaton. */
    static final int MAX_STATES = 2_000;

    /** Code points below this get a transition slot in every deterministic state. */
    private static final int ASCII = 0x80;

    /** Estimated bytes of a deterministic state besides its arrays: objects, key, map entry. */
    private static final long STATE_OVERHEAD = 160;

    /** Estimated bytes of one cached transition on a non-ASCII code point. */
    private static final long TRANSITION_OVERHEAD = 64;

    private final RegexProgram program;
    private final boolean search;
    private final int generation;
    private final AtomicLong bytes = new AtomicLong();
    private final Map<StateKey, State> states = new ConcurrentHashMap<>();
    private final @Nullable State start;

    LazyDfa(RegexProgram program, boolean search, int generation) {
        this.program = program;
        this.search = search;
        this.generation = generation;
        this.start = closureState(null, -1, true);
    }

    /**
     * Reserves memory for a new state or transition. Returns false if this automaton is full or
     * discarded; if all automata together are full, discards them all and returns false.
     */
    private boolean reserve(long cost) {
        if (generation != IRegexp.dfaGeneration()) {
            return false;
        }
        if (bytes.addAndGet(cost) > IRegexp.MAX_DFA_BYTES_PER_AUTOMATON) {
            bytes.addAndGet(-cost);
            return false;
        }
        if (!IRegexp.reserveShared(cost, generation)) {
            bytes.addAndGet(-cost);
            return false;
        }
        return true;
    }

    private void release(long cost) {
        bytes.addAndGet(-cost);
        IRegexp.releaseShared(cost, generation);
    }

    /** Returns the result, or null if the state budget is exhausted. */
    @Nullable
    Boolean run(String input) {
        State state = start;
        if (state == null) {
            return null;
        }
        if (search && state.match) {
            return true;
        }
        int length = input.length();
        int pos = 0;
        while (pos < length) {
            char c = input.charAt(pos);
            int cp;
            if (c < ASCII) {
                cp = c;
                pos++;
            } else {
                cp = input.codePointAt(pos);
                pos += Character.charCount(cp);
            }
            State following = state.transition(cp);
            if (following == null) {
                following = closureState(state, cp, false);
                if (following == null) {
                    return null;
                }
                state.cache(cp, following);
            }
            state = following;
            if (search && state.match) {
                return true;
            }
            if (!search && state.dead) {
                return false;
            }
        }
        return state.match || state.acceptsAtEnd();
    }

    /**
     * Computes the state after reading {@code cp} in {@code from}, or the start state if
     * {@code from} is null. Returns null if the state budget is exhausted.
     */
    private @Nullable State closureState(@Nullable State from, int cp, boolean atStart) {
        boolean[] visited = new boolean[program.ops.length];
        int[] chars = new int[program.ops.length];
        int[] ends = new int[program.ops.length];
        int[] counts = new int[3]; // chars, ends, match
        int[] stack = new int[2 * program.ops.length + 2];
        if (from == null) {
            closure(0, atStart, visited, chars, ends, counts, stack);
        } else {
            for (int pc : from.chars) {
                if (program.sets[pc].matches(cp)) {
                    closure(program.next[pc], false, visited, chars, ends, counts, stack);
                }
            }
            if (search) {
                closure(0, false, visited, chars, ends, counts, stack);
            }
        }
        StateKey key = new StateKey(sorted(chars, counts[0]), sorted(ends, counts[1]), counts[2] > 0);
        State state = states.get(key);
        if (state != null) {
            return state;
        }
        if (states.size() >= MAX_STATES) {
            return null;
        }
        long cost = STATE_OVERHEAD + 4L * (key.chars.length + key.ends.length + ASCII);
        if (!reserve(cost)) {
            return null;
        }
        State created = new State(key.chars, key.ends, key.match);
        State existing = states.putIfAbsent(key, created);
        if (existing != null) {
            release(cost);
            return existing;
        }
        return created;
    }

    private void closure(
            int startPc, boolean atStart, boolean[] visited, int[] chars, int[] ends, int[] counts, int[] stack) {
        int top = 0;
        stack[top++] = startPc;
        while (top > 0) {
            int pc = stack[--top];
            if (visited[pc]) {
                continue;
            }
            visited[pc] = true;
            switch (program.ops[pc]) {
                case CHAR -> chars[counts[0]++] = pc;
                case END -> ends[counts[1]++] = pc;
                case MATCH -> counts[2] = 1;
                case BEGIN -> {
                    if (atStart) {
                        stack[top++] = program.next[pc];
                    }
                }
                case SPLIT -> {
                    stack[top++] = program.alt[pc];
                    stack[top++] = program.next[pc];
                }
                case JUMP -> stack[top++] = program.next[pc];
                default -> throw new IllegalStateException();
            }
        }
    }

    private int[] sorted(int[] values, int count) {
        int[] copy = java.util.Arrays.copyOf(values, count);
        java.util.Arrays.sort(copy);
        return copy;
    }

    /** Whether MATCH is reachable from a pending END instruction once the input is exhausted. */
    private boolean reachesMatchAtEnd(int startPc) {
        boolean[] visited = new boolean[program.ops.length];
        int[] stack = new int[2 * program.ops.length + 2];
        int top = 0;
        stack[top++] = startPc;
        while (top > 0) {
            int pc = stack[--top];
            if (visited[pc]) {
                continue;
            }
            visited[pc] = true;
            switch (program.ops[pc]) {
                case MATCH -> {
                    return true;
                }
                case END, JUMP -> stack[top++] = program.next[pc];
                case SPLIT -> {
                    stack[top++] = program.alt[pc];
                    stack[top++] = program.next[pc];
                }
                default -> {
                    // CHAR needs input; BEGIN fails because the input is not empty.
                }
            }
        }
        return false;
    }

    private final class State {
        final int[] chars;
        final int[] ends;
        final boolean match;
        final boolean dead;
        /** Transitions on ASCII characters, indexed directly: one lookup per step. */
        final State[] ascii = new State[ASCII];
        /** Transitions on other code points, created on first use. */
        volatile @Nullable Map<Integer, State> other;

        volatile @Nullable Boolean acceptsAtEnd;

        State(int[] chars, int[] ends, boolean match) {
            this.chars = chars;
            this.ends = ends;
            this.match = match;
            this.dead = chars.length == 0 && ends.length == 0 && !match;
        }

        @Nullable
        State transition(int cp) {
            if (cp < ASCII) {
                return ascii[cp];
            }
            Map<Integer, State> transitions = other;
            return transitions == null ? null : transitions.get(cp);
        }

        void cache(int cp, State state) {
            if (cp < ASCII) {
                ascii[cp] = state;
                return;
            }
            Map<Integer, State> transitions = other;
            if (transitions == null) {
                synchronized (this) {
                    transitions = other;
                    if (transitions == null) {
                        if (!reserve(TRANSITION_OVERHEAD)) {
                            return;
                        }
                        transitions = new ConcurrentHashMap<>();
                        other = transitions;
                    }
                }
            }
            if (transitions.size() < MAX_STATES && !transitions.containsKey(cp) && reserve(TRANSITION_OVERHEAD)) {
                if (transitions.putIfAbsent(cp, state) != null) {
                    release(TRANSITION_OVERHEAD);
                }
            }
        }

        boolean acceptsAtEnd() {
            Boolean cached = acceptsAtEnd;
            if (cached == null) {
                boolean accepts = false;
                for (int pc : ends) {
                    if (reachesMatchAtEnd(program.next[pc])) {
                        accepts = true;
                        break;
                    }
                }
                cached = accepts;
                acceptsAtEnd = cached;
            }
            return cached;
        }
    }

    int generation() {
        return generation;
    }

    /** Estimated bytes held by this automaton; for tests. */
    long bytes() {
        return bytes.get();
    }

    /** Identity of a deterministic state. */
    private static final class StateKey {
        final int[] chars;
        final int[] ends;
        final boolean match;
        private final int hash;

        StateKey(int[] chars, int[] ends, boolean match) {
            this.chars = chars;
            this.ends = ends;
            this.match = match;
            this.hash = 31 * (31 * java.util.Arrays.hashCode(chars) + java.util.Arrays.hashCode(ends))
                    + Boolean.hashCode(match);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StateKey other)) {
                return false;
            }

            return match == other.match
                    && java.util.Arrays.equals(chars, other.chars)
                    && java.util.Arrays.equals(ends, other.ends);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
