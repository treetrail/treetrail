package com.christophsens.jsonpath.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * I-Regexp (RFC 9485), the interoperable regular expression format used by the {@code match()}
 * and {@code search()} functions.
 *
 * <p>An expression is parsed strictly against the I-Regexp grammar and compiled into a
 * nondeterministic automaton that is simulated without backtracking (Thompson's construction).
 * Matching therefore takes time linear in the length of the input for a given expression, so
 * expressions from untrusted sources cannot cause catastrophic backtracking.
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

    private static final Map<String, Optional<IRegexp>> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Optional<IRegexp>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

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
    }

    /** Returns the compiled expression, or empty if {@code regexp} is not a valid I-Regexp. */
    public static Optional<IRegexp> compile(String regexp) {
        return CACHE.computeIfAbsent(regexp, IRegexp::doCompile);
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
        return run(input, false);
    }

    /** Whether some substring of the input matches ({@code search()}). */
    public boolean find(String input) {
        return run(input, true);
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
