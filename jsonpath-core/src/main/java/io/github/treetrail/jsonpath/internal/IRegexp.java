package io.github.treetrail.jsonpath.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
 * If an expression creates more than {@link #MAX_DFA_STATES} such states, matching falls back to
 * the plain set-of-states simulation, which is slower but still linear.
 *
 * <p>Expressions that expand into more than {@link #MAX_PROGRAM_SIZE} instructions, for example
 * through large counted repetitions such as {@code (a{1000}){1000}}, are rejected like invalid
 * expressions (RFC 9485, section 8, allows implementations to set such limits).
 */
public final class IRegexp {

    /** Maximum number of automaton instructions per expression. */
    static final int MAX_PROGRAM_SIZE = 20_000;

    /** Maximum nesting of groups, to bound recursion on hostile input. */
    static final int MAX_NESTING = 100;

    private static final int CACHE_SIZE = 256;

    /** Maximum number of cached deterministic states per expression and mode. */
    static final int MAX_DFA_STATES = 2_000;

    /** Compiled expressions; reads are lock-free because filters call match() once per node. */
    private static final Map<String, Optional<IRegexp>> CACHE = new ConcurrentHashMap<>();

    // Instructions of the compiled automaton.
    private static final int CHAR = 0;
    private static final int SPLIT = 1;
    private static final int JUMP = 2;
    private static final int BEGIN = 3;
    private static final int END = 4;
    private static final int MATCH = 5;

    private final int[] ops;
    private final int[] next;
    private final int[] alt;
    private final CharSet[] sets;
    private final LazyDfa matchDfa;
    private final LazyDfa searchDfa;

    private IRegexp(Program program) {
        int size = program.ops.size();
        ops = new int[size];
        next = new int[size];
        alt = new int[size];
        sets = new CharSet[size];
        for (int i = 0; i < size; i++) {
            ops[i] = program.ops.get(i);
            next[i] = program.next.get(i);
            alt[i] = program.alt.get(i);
            sets[i] = program.sets.get(i);
        }
        matchDfa = new LazyDfa(false);
        searchDfa = new LazyDfa(true);
    }

    /** Returns the compiled expression, or empty if {@code regexp} is not a valid I-Regexp. */
    public static Optional<IRegexp> compile(String regexp) {
        Optional<IRegexp> cached = CACHE.get(regexp);
        if (cached != null) {
            return cached;
        }
        Optional<IRegexp> compiled = doCompile(regexp);
        if (CACHE.size() >= CACHE_SIZE) {
            // Expressions usually come from a handful of queries; a full cache means unusual input.
            CACHE.clear();
        }
        CACHE.put(regexp, compiled);
        return compiled;
    }

    private static Optional<IRegexp> doCompile(String regexp) {
        try {
            Node tree = new RegexParser(regexp).parse();
            Program program = new Program();
            program.emit(tree);
            program.add(MATCH, -1, -1, null);
            return Optional.of(new IRegexp(program));
        } catch (InvalidRegexp e) {
            return Optional.empty();
        }
    }

    /** Whether the whole input matches ({@code match()}). */
    public boolean matches(String input) {
        Boolean result = input.isEmpty() ? null : matchDfa.run(input);
        return result != null ? result : run(input, false);
    }

    /** Whether some substring of the input matches ({@code search()}). */
    public boolean find(String input) {
        Boolean result = input.isEmpty() ? null : searchDfa.run(input);
        return result != null ? result : run(input, true);
    }

    /**
     * Lazily built deterministic automaton for non-empty inputs. Each state is a set of CHAR
     * instructions waiting for the next code point, plus END instructions that only pass at the end
     * of the input, plus whether MATCH was reached. States and transitions are shared between threads:
     * states are immutable apart from their transition caches, whose entries are published racily but
     * safely (all fields of a state are final).
     */
    private final class LazyDfa {
        private final boolean search;
        private final Map<StateKey, State> states = new ConcurrentHashMap<>();
        private final State start;

        LazyDfa(boolean search) {
            this.search = search;
            this.start = closureState(null, -1, true);
        }

        /** Returns the result, or null if the state budget is exhausted. */
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
                if (c < 0x80) {
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
        private State closureState(State from, int cp, boolean atStart) {
            boolean[] visited = new boolean[ops.length];
            int[] chars = new int[ops.length];
            int[] ends = new int[ops.length];
            int[] counts = new int[3]; // chars, ends, match
            int[] stack = new int[2 * ops.length + 2];
            if (from == null) {
                closure(0, atStart, visited, chars, ends, counts, stack);
            } else {
                for (int pc : from.chars) {
                    if (sets[pc].matches(cp)) {
                        closure(next[pc], false, visited, chars, ends, counts, stack);
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
            if (states.size() >= MAX_DFA_STATES) {
                return null;
            }
            State created = new State(key.chars, key.ends, key.match);
            State existing = states.putIfAbsent(key, created);
            return existing != null ? existing : created;
        }

        private void closure(int startPc, boolean atStart, boolean[] visited, int[] chars, int[] ends,
                int[] counts, int[] stack) {
            int top = 0;
            stack[top++] = startPc;
            while (top > 0) {
                int pc = stack[--top];
                if (visited[pc]) {
                    continue;
                }
                visited[pc] = true;
                switch (ops[pc]) {
                    case CHAR:
                        chars[counts[0]++] = pc;
                        break;
                    case END:
                        ends[counts[1]++] = pc;
                        break;
                    case MATCH:
                        counts[2] = 1;
                        break;
                    case BEGIN:
                        if (atStart) {
                            stack[top++] = next[pc];
                        }
                        break;
                    case SPLIT:
                        stack[top++] = alt[pc];
                        stack[top++] = next[pc];
                        break;
                    case JUMP:
                        stack[top++] = next[pc];
                        break;
                    default:
                        throw new IllegalStateException();
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
            boolean[] visited = new boolean[ops.length];
            int[] stack = new int[2 * ops.length + 2];
            int top = 0;
            stack[top++] = startPc;
            while (top > 0) {
                int pc = stack[--top];
                if (visited[pc]) {
                    continue;
                }
                visited[pc] = true;
                switch (ops[pc]) {
                    case MATCH:
                        return true;
                    case END:
                    case JUMP:
                        stack[top++] = next[pc];
                        break;
                    case SPLIT:
                        stack[top++] = alt[pc];
                        stack[top++] = next[pc];
                        break;
                    default:
                        // CHAR needs input; BEGIN fails because the input is not empty.
                        break;
                }
            }
            return false;
        }

        private final class State {
            final int[] chars;
            final int[] ends;
            final boolean match;
            final boolean dead;
            final State[] ascii = new State[0x80];
            final Map<Integer, State> other = new ConcurrentHashMap<>();
            volatile Boolean acceptsAtEnd;

            State(int[] chars, int[] ends, boolean match) {
                this.chars = chars;
                this.ends = ends;
                this.match = match;
                this.dead = chars.length == 0 && ends.length == 0 && !match;
            }

            State transition(int cp) {
                return cp < 0x80 ? ascii[cp] : other.get(cp);
            }

            void cache(int cp, State state) {
                if (cp < 0x80) {
                    ascii[cp] = state;
                } else if (other.size() < MAX_DFA_STATES) {
                    other.put(cp, state);
                }
            }

            boolean acceptsAtEnd() {
                Boolean cached = acceptsAtEnd;
                if (cached == null) {
                    boolean accepts = false;
                    for (int pc : ends) {
                        if (reachesMatchAtEnd(next[pc])) {
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
            if (!(o instanceof StateKey)) {
                return false;
            }
            StateKey other = (StateKey) o;
            return match == other.match && java.util.Arrays.equals(chars, other.chars)
                    && java.util.Arrays.equals(ends, other.ends);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private boolean run(String input, boolean search) {
        Simulation sim = new Simulation(input, search);
        int length = input.length();
        if (sim.add(0, 0)) {
            return true;
        }
        int pos = 0;
        while (pos < length) {
            int cp = input.codePointAt(pos);
            int nextPos = pos + Character.charCount(cp);
            if (sim.step(cp, nextPos)) {
                return true;
            }
            if (sim.isDead()) {
                return false;
            }
            pos = nextPos;
        }
        return false;
    }

    /** Set-of-states simulation; each instruction is visited at most once per input position. */
    private final class Simulation {
        private final String input;
        private final boolean search;
        private int[] current;
        private int currentSize;
        private int[] following;
        private int followingSize;
        private final int[] visited;
        private int generation = 1;
        private final int[] stack;

        Simulation(String input, boolean search) {
            this.input = input;
            this.search = search;
            this.current = new int[ops.length];
            this.following = new int[ops.length];
            this.visited = new int[ops.length];
            this.stack = new int[2 * ops.length + 2];
        }

        /** Adds the closure of {@code pc} at {@code pos} to the current list; returns true on a match. */
        boolean add(int pc, int pos) {
            boolean matched = closure(pc, pos, false);
            return matched;
        }

        /** Advances all threads over {@code cp}; returns true on a match. */
        boolean step(int cp, int pos) {
            generation++;
            followingSize = 0;
            boolean matched = false;
            for (int i = 0; i < currentSize; i++) {
                int pc = current[i];
                if (sets[pc].matches(cp)) {
                    matched |= closure(next[pc], pos, true);
                }
            }
            if (search) {
                matched |= closure(0, pos, true);
            }
            int[] tmp = current;
            current = following;
            following = tmp;
            currentSize = followingSize;
            return matched;
        }

        boolean isDead() {
            return currentSize == 0 && !search;
        }

        private boolean closure(int start, int pos, boolean intoFollowing) {
            boolean matched = false;
            int top = 0;
            stack[top++] = start;
            while (top > 0) {
                int pc = stack[--top];
                if (visited[pc] == generation) {
                    continue;
                }
                visited[pc] = generation;
                switch (ops[pc]) {
                    case CHAR:
                        if (intoFollowing) {
                            following[followingSize++] = pc;
                        } else {
                            current[currentSize++] = pc;
                        }
                        break;
                    case SPLIT:
                        stack[top++] = alt[pc];
                        stack[top++] = next[pc];
                        break;
                    case JUMP:
                        stack[top++] = next[pc];
                        break;
                    case BEGIN:
                        if (pos == 0) {
                            stack[top++] = next[pc];
                        }
                        break;
                    case END:
                        if (pos == input.length()) {
                            stack[top++] = next[pc];
                        }
                        break;
                    case MATCH:
                        if (search || pos == input.length()) {
                            matched = true;
                        }
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
            return matched;
        }
    }

    // ---- syntax tree ----

    /** Node of the parsed expression. */
    interface Node {
    }

    record Alternation(List<Node> branches) implements Node {
    }

    record Sequence(List<Node> pieces) implements Node {
    }

    /** {@code max} is -1 for "unbounded". */
    record Repeat(Node atom, int min, int max) implements Node {
    }

    record Chars(CharSet set) implements Node {
    }

    record Begin() implements Node {
    }

    record End() implements Node {
    }

    // ---- compiler ----

    /** Thompson construction of the automaton. */
    private static final class Program {
        final List<Integer> ops = new ArrayList<>();
        final List<Integer> next = new ArrayList<>();
        final List<Integer> alt = new ArrayList<>();
        final List<CharSet> sets = new ArrayList<>();

        int add(int op, int nextPc, int altPc, CharSet set) {
            if (ops.size() >= MAX_PROGRAM_SIZE) {
                throw new InvalidRegexp();
            }
            ops.add(op);
            next.add(nextPc);
            alt.add(altPc);
            sets.add(set);
            return ops.size() - 1;
        }

        int pc() {
            return ops.size();
        }

        void emit(Node node) {
            if (node instanceof Sequence) {
                for (Node piece : ((Sequence) node).pieces()) {
                    emit(piece);
                }
            } else if (node instanceof Chars) {
                add(CHAR, pc() + 1, -1, ((Chars) node).set());
            } else if (node instanceof Begin) {
                add(BEGIN, pc() + 1, -1, null);
            } else if (node instanceof End) {
                add(END, pc() + 1, -1, null);
            } else if (node instanceof Alternation) {
                emitAlternation(((Alternation) node).branches());
            } else if (node instanceof Repeat) {
                emitRepeat((Repeat) node);
            } else {
                throw new IllegalStateException();
            }
        }

        private void emitAlternation(List<Node> branches) {
            List<Integer> jumps = new ArrayList<>();
            for (int i = 0; i < branches.size() - 1; i++) {
                int split = add(SPLIT, pc() + 1, -1, null);
                emit(branches.get(i));
                jumps.add(add(JUMP, -1, -1, null));
                alt.set(split, pc());
            }
            emit(branches.get(branches.size() - 1));
            for (int jump : jumps) {
                next.set(jump, pc());
            }
        }

        private void emitRepeat(Repeat repeat) {
            for (int i = 0; i < repeat.min(); i++) {
                emit(repeat.atom());
            }
            if (repeat.max() < 0) {
                int loop = add(SPLIT, pc() + 1, -1, null);
                emit(repeat.atom());
                add(JUMP, loop, -1, null);
                alt.set(loop, pc());
                return;
            }
            List<Integer> splits = new ArrayList<>();
            for (int i = repeat.min(); i < repeat.max(); i++) {
                splits.add(add(SPLIT, pc() + 1, -1, null));
                emit(repeat.atom());
            }
            for (int split : splits) {
                alt.set(split, pc());
            }
        }
    }

    // ---- parser (RFC 9485, section 3) ----

    private static final class RegexParser {
        private final String source;
        private int pos;
        private int nesting;

        RegexParser(String source) {
            this.source = source;
        }

        Node parse() {
            Node node = regexp();
            if (pos != source.length()) {
                throw new InvalidRegexp();
            }
            return node;
        }

        private Node regexp() {
            List<Node> branches = new ArrayList<>();
            branches.add(branch());
            while (peek() == '|') {
                pos++;
                branches.add(branch());
            }
            return branches.size() == 1 ? branches.get(0) : new Alternation(branches);
        }

        private Node branch() {
            List<Node> pieces = new ArrayList<>();
            while (pos < source.length() && peek() != '|' && peek() != ')') {
                pieces.add(piece());
            }
            return pieces.size() == 1 ? pieces.get(0) : new Sequence(pieces);
        }

        private Node piece() {
            Node atom = atom();
            int c = peek();
            if (c == '*') {
                pos++;
                return new Repeat(atom, 0, -1);
            }
            if (c == '+') {
                pos++;
                return new Repeat(atom, 1, -1);
            }
            if (c == '?') {
                pos++;
                return new Repeat(atom, 0, 1);
            }
            if (c == '{') {
                pos++;
                int min = quantity();
                int max = min;
                if (peek() == ',') {
                    pos++;
                    max = peek() == '}' ? -1 : quantity();
                    if (max >= 0 && max < min) {
                        throw new InvalidRegexp();
                    }
                }
                expect('}');
                return new Repeat(atom, min, max);
            }
            return atom;
        }

        private int quantity() {
            int start = pos;
            while (pos < source.length() && source.charAt(pos) >= '0' && source.charAt(pos) <= '9') {
                pos++;
            }
            if (start == pos) {
                throw new InvalidRegexp();
            }
            try {
                return Integer.parseInt(source.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new InvalidRegexp();
            }
        }

        private Node atom() {
            int c = next();
            switch (c) {
                case '(':
                    if (++nesting > MAX_NESTING) {
                        throw new InvalidRegexp();
                    }
                    Node inner = regexp();
                    expect(')');
                    nesting--;
                    return inner;
                case '.':
                    // I-Regexp '.' matches any character except line feed and carriage return.
                    return new Chars(CharSet.ranges(true, new int[] {'\n', '\n', '\r', '\r'}));
                case '\\':
                    return new Chars(escape());
                case '[':
                    return new Chars(charClass());
                // The I-Regexp grammar lists '^' and '$' as normal characters, but the Compliance
                // Test Suite ("explicit caret", "explicit dollar") expects them to act as anchors,
                // as in the regex dialects RFC 9485 section 5 maps to. We follow the test suite.
                case '^':
                    return new Begin();
                case '$':
                    return new End();
                default:
                    if (!isNormalChar(c)) {
                        throw new InvalidRegexp();
                    }
                    return new Chars(CharSet.single(c));
            }
        }

        private static boolean isNormalChar(int c) {
            return (c <= 0x27) || c == ',' || c == '-' || (c >= 0x2F && c <= 0x3E) || (c >= 0x40 && c <= 0x5A)
                    || (c >= 0x5E && c <= 0x7A) || (c >= 0x7E && c <= 0xD7FF) || (c >= 0xE000 && c <= 0x10FFFF);
        }

        /** Parses an escape after the backslash: a single character or a category. */
        private CharSet escape() {
            int c = next();
            if (c == 'p' || c == 'P') {
                return CharSet.category(category(), c == 'P');
            }
            return CharSet.single(singleCharEscape(c));
        }

        private static int singleCharEscape(int c) {
            switch (c) {
                case 'n':
                    return '\n';
                case 'r':
                    return '\r';
                case 't':
                    return '\t';
                case '(': case ')': case '*': case '+': case '-': case '.': case '?':
                case '[': case '\\': case ']': case '^': case '{': case '|': case '}':
                    return c;
                default:
                    throw new InvalidRegexp();
            }
        }

        private long category() {
            expect('{');
            int start = pos;
            while (pos < source.length() && source.charAt(pos) != '}') {
                pos++;
            }
            String name = source.substring(start, pos);
            expect('}');
            Long mask = CharSet.CATEGORIES.get(name);
            if (mask == null) {
                throw new InvalidRegexp();
            }
            return mask;
        }

        private CharSet charClass() {
            boolean negated = false;
            if (peek() == '^') {
                pos++;
                negated = true;
            }
            CharSet.Builder builder = new CharSet.Builder(negated);
            boolean first = true;
            while (true) {
                int c = peek();
                if (c == ']' && !first) {
                    pos++;
                    return builder.build();
                }
                if (c == '-') {
                    pos++;
                    if (first || peek() == ']') {
                        // A literal '-' is only allowed first or last.
                        builder.range('-', '-');
                        first = false;
                        continue;
                    }
                    throw new InvalidRegexp();
                }
                classItem(builder);
                first = false;
            }
        }

        private void classItem(CharSet.Builder builder) {
            int c = next();
            if (c == '\\') {
                int e = next();
                if (e == 'p' || e == 'P') {
                    builder.category(category(), e == 'P');
                    return;
                }
                rangeFrom(singleCharEscape(e), builder);
                return;
            }
            rangeFrom(classChar(c), builder);
        }

        private void rangeFrom(int lo, CharSet.Builder builder) {
            if (peek() == '-' && pos + 1 < source.length() && source.charAt(pos + 1) != ']') {
                pos++;
                int c = next();
                int hi = c == '\\' ? singleCharEscape(next()) : classChar(c);
                if (hi < lo) {
                    throw new InvalidRegexp();
                }
                builder.range(lo, hi);
            } else {
                builder.range(lo, lo);
            }
        }

        private static int classChar(int c) {
            if (c == '-' || c == '[' || c == ']') {
                throw new InvalidRegexp();
            }
            return c;
        }

        private int peek() {
            return pos < source.length() ? source.codePointAt(pos) : -1;
        }

        private int next() {
            if (pos >= source.length()) {
                throw new InvalidRegexp();
            }
            int c = source.codePointAt(pos);
            if (Character.isSurrogate(source.charAt(pos)) && Character.charCount(c) == 1) {
                throw new InvalidRegexp();
            }
            pos += Character.charCount(c);
            return c;
        }

        private void expect(int c) {
            if (next() != c) {
                throw new InvalidRegexp();
            }
        }
    }

    private static final class InvalidRegexp extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidRegexp() {
            super(null, null, false, false);
        }
    }
}
