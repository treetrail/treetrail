package io.github.treetrail.jsonpath.internal;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Syntax tree of a parsed query. Produced by {@link Parser}, executed by {@link Evaluator}.
 */
public final class Ast {

    private Ast() {
    }

    /** A query: {@code $} or {@code @} followed by segments. */
    public record Query(boolean absolute, List<Segment> segments) {

        /** Whether this is a singular query (RFC 9535, section 2.3.5.1): it selects at most one node. */
        public boolean isSingular() {
            for (Segment segment : segments) {
                if (!segment.singular()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * A child or descendant segment. {@code singular} records whether the segment's syntax is a
     * singular segment ({@code .name}, {@code ['name']} or {@code [0]} without inner blanks).
     */
    public record Segment(boolean descendant, List<Selector> selectors, boolean singular) {
    }

    public sealed interface Selector permits Name, Wildcard, Index, Slice, Filter {
    }

    public record Name(String name) implements Selector {
    }

    public record Wildcard() implements Selector {
    }

    public record Index(long index) implements Selector {
    }

    /** A slice; {@code null} bounds mean "not given". */
    public record Slice(@Nullable Long start, @Nullable Long end, @Nullable Long step) implements Selector {
    }

    public record Filter(Expr expr) implements Selector {
    }

    /** A function argument is an operand or a logical expression. */
    public sealed interface Argument permits Operand, Expr {
    }

    /** Something that produces a value, a node list or a logical value. */
    public sealed interface Operand extends Argument permits Literal, QueryOperand, FunctionCall {
    }

    /** A literal; {@code value} uses the {@link io.github.treetrail.jsonpath.JavaObjectModel} (null = JSON null). */
    public record Literal(@Nullable Object value) implements Operand {
    }

    public record QueryOperand(Query query) implements Operand {
    }

    public record FunctionCall(FunctionDefinition function, List<Argument> arguments) implements Operand {
    }

    /** A logical expression. */
    public sealed interface Expr extends Argument permits Or, And, Not, Paren, Comparison, Test {
    }

    public record Or(List<Expr> operands) implements Expr {
    }

    public record And(List<Expr> operands) implements Expr {
    }

    public record Not(Expr operand) implements Expr {
    }

    public record Paren(Expr operand) implements Expr {
    }

    public record Comparison(Operand left, ComparisonOp op, Operand right) implements Expr {
    }

    /** An existence test ({@code @.a}) or a logical function call ({@code match(@.a, 'x')}). */
    public record Test(Operand operand) implements Expr {
    }

    public enum ComparisonOp {
        EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">=");

        private final String symbol;

        ComparisonOp(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }
}
