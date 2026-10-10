package io.github.treetrail.jsonpath.internal;

import java.util.List;

/** Syntax tree of an I-Regexp (RFC 9485), as {@link RegexParser} builds it. */
sealed interface RegexTree
        permits RegexTree.Alternation,
                RegexTree.Sequence,
                RegexTree.Repeat,
                RegexTree.Chars,
                RegexTree.Begin,
                RegexTree.End {

    record Alternation(List<RegexTree> branches) implements RegexTree {}

    record Sequence(List<RegexTree> pieces) implements RegexTree {}

    /** {@code max} is -1 for "unbounded". */
    record Repeat(RegexTree atom, int min, int max) implements RegexTree {}

    record Chars(CharSet set) implements RegexTree {}

    record Begin() implements RegexTree {}

    record End() implements RegexTree {}
}
