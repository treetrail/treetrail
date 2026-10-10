package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import io.github.treetrail.jsonpath.internal.Ast.And;
import io.github.treetrail.jsonpath.internal.Ast.Argument;
import io.github.treetrail.jsonpath.internal.Ast.Comparison;
import io.github.treetrail.jsonpath.internal.Ast.ComparisonOp;
import io.github.treetrail.jsonpath.internal.Ast.Expr;
import io.github.treetrail.jsonpath.internal.Ast.Filter;
import io.github.treetrail.jsonpath.internal.Ast.FunctionCall;
import io.github.treetrail.jsonpath.internal.Ast.Index;
import io.github.treetrail.jsonpath.internal.Ast.Literal;
import io.github.treetrail.jsonpath.internal.Ast.Name;
import io.github.treetrail.jsonpath.internal.Ast.Not;
import io.github.treetrail.jsonpath.internal.Ast.Operand;
import io.github.treetrail.jsonpath.internal.Ast.Or;
import io.github.treetrail.jsonpath.internal.Ast.Paren;
import io.github.treetrail.jsonpath.internal.Ast.Query;
import io.github.treetrail.jsonpath.internal.Ast.QueryOperand;
import io.github.treetrail.jsonpath.internal.Ast.Segment;
import io.github.treetrail.jsonpath.internal.Ast.Selector;
import io.github.treetrail.jsonpath.internal.Ast.Slice;
import io.github.treetrail.jsonpath.internal.Ast.Test;
import io.github.treetrail.jsonpath.internal.Ast.Wildcard;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Recursive-descent parser for the RFC 9535 grammar (appendix A), including the well-typedness
 * rules for function expressions (section 2.4.3).
 */
public final class Parser {

    /** Largest integer allowed in index and slice selectors: 2^53 - 1 (I-JSON range). */
    private static final long MAX_INT = (1L << 53) - 1;

    /** Maximum nesting of filters, parentheses and function calls, to bound recursion on hostile input. */
    static final int MAX_NESTING = 64;

    private final String src;
    private final Map<String, FunctionDefinition> functions;
    private int pos;
    private int nesting;

    /** Start position of every test expression, so that type errors point at the operand. */
    private final IdentityHashMap<Test, Integer> testPositions = new IdentityHashMap<>();

    private Parser(String src, Map<String, FunctionDefinition> functions) {
        this.src = src;
        this.functions = functions;
    }

    public static Query parse(String expression, Map<String, FunctionDefinition> functions) {
        Parser parser = new Parser(expression, functions);
        if (parser.peek() != '$') {
            throw parser.error("Query must start with '$'");
        }
        parser.pos++;
        Query query = new Query(true, parser.segments());
        if (parser.pos != expression.length()) {
            throw parser.error("Unexpected character");
        }
        return query;
    }

    // ---- segments and selectors ----

    private List<Segment> segments() {
        List<Segment> segments = new ArrayList<>();
        while (true) {
            int save = pos;
            skipBlanks();
            int c = peek();
            if (c == '.' || c == '[') {
                segments.add(segment());
            } else {
                pos = save;
                return List.copyOf(segments);
            }
        }
    }

    private Segment segment() {
        if (src.startsWith("..", pos)) {
            pos += 2;
            int c = peek();
            if (c == '[') {
                return new Segment(true, bracketedSelection(), false);
            }
            if (c == '*') {
                pos++;
                return new Segment(true, List.of(new Wildcard()), false);
            }
            if (isNameFirst(c)) {
                return new Segment(true, List.of(new Name(memberNameShorthand())), false);
            }
            throw error("Expected '[', '*' or a member name after '..'");
        }
        if (peek() == '.') {
            pos++;
            if (peek() == '*') {
                pos++;
                return new Segment(false, List.of(new Wildcard()), false);
            }
            if (isNameFirst(peek())) {
                return new Segment(false, List.of(new Name(memberNameShorthand())), true);
            }
            throw error("Expected '*' or a member name after '.'");
        }
        int start = pos;
        List<Selector> selectors = bracketedSelection();
        String text = src.substring(start, pos);
        boolean singular = selectors.size() == 1
                && (selectors.get(0) instanceof Name || selectors.get(0) instanceof Index)
                && !hasBlankAtEdges(text);
        return new Segment(false, selectors, singular);
    }

    /** Singular segments are written without blanks inside the brackets: {@code ['a']} or {@code [0]}. */
    private static boolean hasBlankAtEdges(String bracketed) {
        return isBlank(bracketed.charAt(1)) || isBlank(bracketed.charAt(bracketed.length() - 2));
    }

    private List<Selector> bracketedSelection() {
        expect('[');
        List<Selector> selectors = new ArrayList<>();
        skipBlanks();
        selectors.add(selector());
        skipBlanks();
        while (peek() == ',') {
            pos++;
            skipBlanks();
            selectors.add(selector());
            skipBlanks();
        }
        expect(']');
        return List.copyOf(selectors);
    }

    private Selector selector() {
        int c = peek();
        if (c == '\'' || c == '"') {
            return new Name(stringLiteral());
        }
        if (c == '*') {
            pos++;
            return new Wildcard();
        }
        if (c == '?') {
            pos++;
            skipBlanks();
            enter();
            Expr expr = logicalOr();
            nesting--;
            checkLogical(expr);
            return new Filter(expr);
        }
        if (c == '-' || isDigit(c) || c == ':') {
            return indexOrSlice();
        }
        throw error("Expected a selector");
    }

    private Selector indexOrSlice() {
        Long start = optionalInt();
        if (start != null) {
            skipBlanks();
        }
        if (peek() != ':') {
            if (start == null) {
                throw error("Expected an integer");
            }
            return new Index(start);
        }
        pos++;
        skipBlanks();
        Long end = optionalInt();
        if (end != null) {
            skipBlanks();
        }
        Long step = null;
        if (peek() == ':') {
            pos++;
            skipBlanks();
            step = optionalInt();
        }
        return new Slice(start, end, step);
    }

    private @Nullable Long optionalInt() {
        int c = peek();
        if (c != '-' && !isDigit(c)) {
            return null;
        }
        int start = pos;
        if (c == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
            if (c == '-') {
                throw error("'-0' is not a valid integer", start);
            }
            if (isDigit(peek())) {
                throw error("Leading zeros are not allowed", start);
            }
            return 0L;
        }
        if (!isDigit(peek())) {
            throw error("Expected a digit");
        }
        while (isDigit(peek())) {
            pos++;
        }
        String digits = src.substring(start, pos);
        long value;
        try {
            value = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw error("Integer out of range", start);
        }
        if (value > MAX_INT || value < -MAX_INT) {
            throw error("Integer out of range", start);
        }
        return value;
    }

    private String memberNameShorthand() {
        int start = pos;
        while (pos < src.length() && isNameChar(peek())) {
            pos += Character.charCount(peek());
        }
        return src.substring(start, pos);
    }

    // ---- filter expressions ----

    private Expr logicalOr() {
        List<Expr> operands = new ArrayList<>();
        operands.add(logicalAnd());
        while (true) {
            int save = pos;
            skipBlanks();
            if (src.startsWith("||", pos)) {
                pos += 2;
                skipBlanks();
                operands.add(logicalAnd());
            } else {
                pos = save;
                break;
            }
        }
        return operands.size() == 1 ? operands.get(0) : new Or(List.copyOf(operands));
    }

    private Expr logicalAnd() {
        List<Expr> operands = new ArrayList<>();
        operands.add(basicExpr());
        while (true) {
            int save = pos;
            skipBlanks();
            if (src.startsWith("&&", pos)) {
                pos += 2;
                skipBlanks();
                operands.add(basicExpr());
            } else {
                pos = save;
                break;
            }
        }
        return operands.size() == 1 ? operands.get(0) : new And(List.copyOf(operands));
    }

    private Expr basicExpr() {
        if (peek() == '!') {
            pos++;
            skipBlanks();
            if (peek() == '(') {
                return new Not(parenExpr());
            }
            int start = pos;
            Operand operand = operand();
            if (operand instanceof Literal) {
                throw error("'!' must be followed by a query, a function or '('", start);
            }
            int save = pos;
            skipBlanks();
            if (comparisonOp() != null) {
                throw error("A comparison must be in parentheses to be negated", save);
            }
            pos = save;
            return new Not(test(operand, start));
        }
        if (peek() == '(') {
            return parenExpr();
        }
        int start = pos;
        Operand left = operand();
        int save = pos;
        skipBlanks();
        ComparisonOp op = comparisonOp();
        if (op == null) {
            pos = save;
            return test(left, start);
        }
        skipBlanks();
        int rightStart = pos;
        Operand right = operand();
        checkComparable(left, start);
        checkComparable(right, rightStart);
        return new Comparison(left, op, right);
    }

    private Test test(Operand operand, int start) {
        Test test = new Test(operand);
        testPositions.put(test, start);
        return test;
    }

    private Expr parenExpr() {
        expect('(');
        enter();
        skipBlanks();
        Expr inner = logicalOr();
        skipBlanks();
        expect(')');
        nesting--;
        return new Paren(inner);
    }

    private void enter() {
        if (++nesting > MAX_NESTING) {
            throw error("Expression is nested more than " + MAX_NESTING + " levels deep");
        }
    }

    private @Nullable ComparisonOp comparisonOp() {
        for (String symbol : new String[] {"==", "!=", "<=", ">=", "<", ">"}) {
            if (src.startsWith(symbol, pos)) {
                pos += symbol.length();
                for (ComparisonOp op : ComparisonOp.values()) {
                    if (op.symbol().equals(symbol)) {
                        return op;
                    }
                }
            }
        }
        return null;
    }

    private Operand operand() {
        int c = peek();
        if (c == '@' || c == '$') {
            pos++;
            return new QueryOperand(new Query(c == '$', segments()));
        }
        if (c == '\'' || c == '"') {
            return new Literal(stringLiteral());
        }
        if (c == '-' || isDigit(c)) {
            return new Literal(number());
        }
        if (c >= 'a' && c <= 'z') {
            int start = pos;
            while (isFunctionNameChar(peek())) {
                pos++;
            }
            String name = src.substring(start, pos);
            if (peek() == '(') {
                return functionCall(name, start);
            }
            return switch (name) {
                case "true" -> new Literal(true);
                case "false" -> new Literal(false);
                case "null" -> new Literal(null);
                default -> throw error("Unknown literal '" + name + "'", start);
            };
        }
        throw error("Expected a query, a literal or a function");
    }

    private FunctionCall functionCall(String name, int start) {
        FunctionDefinition function = functions.get(name);
        if (function == null) {
            throw error("Unknown function '" + name + "'", start);
        }
        expect('(');
        enter();
        skipBlanks();
        List<Argument> arguments = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        if (peek() != ')') {
            while (true) {
                positions.add(pos);
                arguments.add(argument());
                skipBlanks();
                if (peek() == ',') {
                    pos++;
                    skipBlanks();
                } else {
                    break;
                }
            }
        }
        expect(')');
        nesting--;
        if (arguments.size() != function.parameters().size()) {
            throw error(
                    "Function '" + name + "' expects " + function.parameters().size() + " argument(s)", start);
        }
        for (int i = 0; i < arguments.size(); i++) {
            checkArgument(arguments.get(i), function.parameters().get(i), positions.get(i));
        }
        return Functions.specialize(new FunctionCall(function, List.copyOf(arguments)));
    }

    /** An argument is parsed as a logical expression; a bare operand is unwrapped. */
    private Argument argument() {
        Expr expr = logicalOr();
        if (expr instanceof Test test) {
            return test.operand();
        }
        return expr;
    }

    // ---- type checking (RFC 9535, section 2.4.3) ----

    private void checkComparable(Operand operand, int at) {
        if (operand instanceof QueryOperand query && !query.query().isSingular()) {
            throw error("Only singular queries can be compared", at);
        }
        if (operand instanceof FunctionCall call && call.function().result() != FunctionDefinition.Type.VALUE) {
            throw error("Function result cannot be compared", at);
        }
    }

    private void checkLogical(Expr expr) {
        if (expr instanceof Or or) {
            or.operands().forEach(this::checkLogical);
        } else if (expr instanceof And and) {
            and.operands().forEach(this::checkLogical);
        } else if (expr instanceof Not not) {
            checkLogical(not.operand());
        } else if (expr instanceof Paren paren) {
            checkLogical(paren.operand());
        } else if (expr instanceof Test test) {
            Operand operand = test.operand();
            int at = testPositions.getOrDefault(expr, pos);
            if (operand instanceof Literal) {
                throw error("A literal is not a valid test expression", at);
            }
            if (operand instanceof FunctionCall call && call.function().result() == FunctionDefinition.Type.VALUE) {
                throw error(
                        "Function '" + call.function().name() + "' returns a value and cannot be used as a test", at);
            }
        }
    }

    private void checkArgument(Argument argument, FunctionDefinition.Type type, int at) {
        boolean ok = switch (type) {
            case VALUE ->
                argument instanceof Literal
                        || (argument instanceof QueryOperand query
                                && query.query().isSingular())
                        || (argument instanceof FunctionCall call
                                && call.function().result() == FunctionDefinition.Type.VALUE);
            case LOGICAL -> {
                if (argument instanceof Expr expr) {
                    checkLogical(expr);
                    yield true;
                }
                yield argument instanceof QueryOperand
                        || (argument instanceof FunctionCall call
                                && call.function().result() != FunctionDefinition.Type.VALUE);
            }
            case NODES ->
                argument instanceof QueryOperand
                        || (argument instanceof FunctionCall call
                                && call.function().result() == FunctionDefinition.Type.NODES);
        };
        if (!ok) {
            throw error("Argument does not match parameter type " + type, at);
        }
    }

    // ---- literals ----

    private BigDecimal number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
            if (isDigit(peek())) {
                throw error("Leading zeros are not allowed", start);
            }
        } else if (isDigit(peek())) {
            while (isDigit(peek())) {
                pos++;
            }
        } else {
            throw error("Expected a digit");
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) {
                throw error("Expected a digit after '.'");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                throw error("Expected a digit in the exponent");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        try {
            return new BigDecimal(src.substring(start, pos));
        } catch (NumberFormatException | ArithmeticException e) {
            throw error("Number out of range", start);
        }
    }

    private String stringLiteral() {
        int quote = src.charAt(pos);
        int start = pos;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw error("Unterminated string", start);
            }
            int c = src.codePointAt(pos);
            if (c == quote) {
                pos++;
                return sb.toString();
            }
            if (c == '\\') {
                pos++;
                escape(quote, sb);
                continue;
            }
            if (c < 0x20) {
                throw error("Control characters must be escaped");
            }
            if (Character.isSurrogate(src.charAt(pos)) && Character.charCount(c) == 1) {
                throw error("Unpaired surrogate");
            }
            sb.appendCodePoint(c);
            pos += Character.charCount(c);
        }
    }

    private void escape(int quote, StringBuilder sb) {
        if (pos >= src.length()) {
            throw error("Unterminated escape");
        }
        char c = src.charAt(pos++);
        switch (c) {
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case '/' -> sb.append('/');
            case '\\' -> sb.append('\\');
            case '\'', '"' -> {
                if (c != quote) {
                    throw error("Invalid escape", pos - 2);
                }
                sb.append(c);
            }
            case 'u' -> {
                int unit = hex4();
                if (unit >= 0xD800 && unit <= 0xDBFF) {
                    if (!src.startsWith("\\u", pos)) {
                        throw error("High surrogate must be followed by a low surrogate");
                    }
                    pos += 2;
                    int low = hex4();
                    if (low < 0xDC00 || low > 0xDFFF) {
                        throw error("High surrogate must be followed by a low surrogate");
                    }
                    sb.append((char) unit).append((char) low);
                } else if (unit >= 0xDC00 && unit <= 0xDFFF) {
                    throw error("Unpaired low surrogate");
                } else {
                    sb.append((char) unit);
                }
            }
            default -> throw error("Invalid escape", pos - 2);
        }
    }

    private int hex4() {
        if (pos + 4 > src.length()) {
            throw error("Expected four hex digits");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            char c = src.charAt(pos + i);
            boolean hex = isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                throw error("Expected four hex digits");
            }
            int digit = Character.digit(c, 16);
            value = value * 16 + digit;
        }
        pos += 4;
        return value;
    }

    // ---- character classes ----

    private static boolean isBlank(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isNameFirst(int c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_'
                || (c >= 0x80 && c <= 0xD7FF)
                || (c >= 0xE000 && c <= 0x10FFFF);
    }

    private static boolean isNameChar(int c) {
        return isNameFirst(c) || isDigit(c);
    }

    private static boolean isFunctionNameChar(int c) {
        return (c >= 'a' && c <= 'z') || c == '_' || isDigit(c);
    }

    private void skipBlanks() {
        while (pos < src.length() && isBlank(src.charAt(pos))) {
            pos++;
        }
    }

    private int peek() {
        if (pos >= src.length()) {
            return -1;
        }
        char c = src.charAt(pos);
        if (Character.isHighSurrogate(c) && pos + 1 < src.length() && Character.isLowSurrogate(src.charAt(pos + 1))) {
            return src.codePointAt(pos);
        }
        // Unpaired surrogates are returned as is; they match no grammar rule.
        return c;
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private JsonPathSyntaxException error(String reason) {
        return error(reason, pos);
    }

    private JsonPathSyntaxException error(String reason, int at) {
        return new JsonPathSyntaxException(reason, src, at);
    }
}
