package io.github.treetrail.jsonpath.internal;

import static io.github.treetrail.jsonpath.internal.RegexProgram.BEGIN;
import static io.github.treetrail.jsonpath.internal.RegexProgram.CHAR;
import static io.github.treetrail.jsonpath.internal.RegexProgram.END;
import static io.github.treetrail.jsonpath.internal.RegexProgram.JUMP;
import static io.github.treetrail.jsonpath.internal.RegexProgram.MATCH;
import static io.github.treetrail.jsonpath.internal.RegexProgram.SPLIT;

/** Set-of-states simulation; each instruction is visited at most once per input position. */
final class RegexSimulation {

    private final RegexProgram program;
    private final String input;
    private final boolean search;
    private int[] current;
    private int currentSize;
    private int[] following;
    private int followingSize;
    private final int[] visited;
    private int generation = 1;
    private final int[] stack;

    private RegexSimulation(RegexProgram program, String input, boolean search) {
        this.program = program;
        this.input = input;
        this.search = search;
        this.current = new int[program.ops.length];
        this.following = new int[program.ops.length];
        this.visited = new int[program.ops.length];
        this.stack = new int[2 * program.ops.length + 2];
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
            if (program.sets[pc].matches(cp)) {
                matched |= closure(program.next[pc], pos, true);
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
            switch (program.ops[pc]) {
                case CHAR -> {
                    if (intoFollowing) {
                        following[followingSize++] = pc;
                    } else {
                        current[currentSize++] = pc;
                    }
                }
                case SPLIT -> {
                    stack[top++] = program.alt[pc];
                    stack[top++] = program.next[pc];
                }
                case JUMP -> stack[top++] = program.next[pc];
                case BEGIN -> {
                    if (pos == 0) {
                        stack[top++] = program.next[pc];
                    }
                }
                case END -> {
                    if (pos == input.length()) {
                        stack[top++] = program.next[pc];
                    }
                }
                case MATCH -> {
                    if (search || pos == input.length()) {
                        matched = true;
                    }
                }
                default -> throw new IllegalStateException();
            }
        }
        return matched;
    }

    /** Whether the input matches (or, with {@code search}, contains a match), simulating all threads at once. */
    static boolean run(RegexProgram program, String input, boolean search) {
        RegexSimulation sim = new RegexSimulation(program, input, search);
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
}
