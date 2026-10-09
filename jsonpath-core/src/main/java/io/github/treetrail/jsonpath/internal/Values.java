package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import io.github.treetrail.jsonpath.internal.Ast.ComparisonOp;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Comparison of values (RFC 9535, section 2.3.5.2.2).
 */
public final class Values {

    private Values() {}

    /**
     * Compares two values. Numbers that are not finite (see {@link JsonModel#numberValue}) are neither
     * equal to nor ordered against any value; {@code !=} is the negation of {@code ==}.
     *
     * @param maxDepth how deep arrays and objects are compared before the comparison fails
     */
    static boolean compare(Val left, ComparisonOp op, Val right, int maxDepth) {
        switch (op) {
            case EQ:
                return equal(left, right, maxDepth);
            case NE:
                return !equal(left, right, maxDepth);
            case LT:
                return less(left, right);
            case LE:
                return less(left, right) || equal(left, right, maxDepth);
            case GT:
                return less(right, left);
            case GE:
                return less(right, left) || equal(left, right, maxDepth);
            default:
                throw new IllegalStateException();
        }
    }

    private static boolean equal(Val a, Val b, int maxDepth) {
        if (a.isNothing() || b.isNothing()) {
            return a.isNothing() && b.isNothing();
        }
        return deepEqual(a.model(), a.value(), b.model(), b.value(), maxDepth);
    }

    private static boolean less(Val a, Val b) {
        if (a.isNothing() || b.isNothing()) {
            return false;
        }
        JsonKind ka = a.kind();
        JsonKind kb = b.kind();
        if (ka == JsonKind.NUMBER && kb == JsonKind.NUMBER) {
            return compareNumbers(a.model(), a.value(), b.model(), b.value()) < 0;
        }
        if (ka == JsonKind.STRING && kb == JsonKind.STRING) {
            return compareCodePoints(a.string(), b.string()) < 0;
        }
        return false;
    }

    /** Compares strings by Unicode scalar values (UTF-16 order differs above U+FFFF). */
    private static int compareCodePoints(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Boolean.compare(i < a.length(), j < b.length());
    }

    /** A pair of values still to compare, at a depth below the compared values. */
    private static final class Pending {
        final @Nullable Object a;
        final @Nullable Object b;
        final int depth;

        Pending(@Nullable Object a, @Nullable Object b, int depth) {
            this.a = a;
            this.b = b;
            this.depth = depth;
        }
    }

    /**
     * Whether two values are equal as JSON values, the semantics of {@code ==} in filters: numbers by value,
     * strings by content, arrays element by element, objects member by member regardless of order.
     */
    public static boolean jsonEquals(
            JsonModel<@Nullable Object> ma,
            @Nullable Object a,
            JsonModel<@Nullable Object> mb,
            @Nullable Object b,
            int maxDepth) {
        return deepEqual(ma, a, mb, b, maxDepth);
    }

    /** Structural equality, with an explicit stack so that deep values cannot overflow the call stack. */
    private static boolean deepEqual(
            JsonModel<@Nullable Object> ma,
            @Nullable Object a,
            JsonModel<@Nullable Object> mb,
            @Nullable Object b,
            int maxDepth) {
        JsonKind kind = ma.kind(a);
        if (kind != mb.kind(b)) {
            return false;
        }
        if (kind != JsonKind.ARRAY && kind != JsonKind.OBJECT) {
            // Most comparisons in filters are between scalars; they need no stack.
            return scalarEqual(kind, ma, a, mb, b);
        }
        Deque<Pending> pending = new ArrayDeque<>();
        pending.push(new Pending(a, b, 0));
        while (!pending.isEmpty()) {
            Pending p = pending.pop();
            kind = ma.kind(p.a);
            if (kind != mb.kind(p.b)) {
                return false;
            }
            if (kind == JsonKind.ARRAY) {
                int size = ma.size(p.a);
                if (size != mb.size(p.b)) {
                    return false;
                }
                if (size > 0 && p.depth >= maxDepth) {
                    throw Evaluator.tooDeep(maxDepth);
                }
                for (int i = 0; i < size; i++) {
                    pending.push(new Pending(ma.element(p.a, i), mb.element(p.b, i), p.depth + 1));
                }
            } else if (kind == JsonKind.OBJECT) {
                int count = ma.memberCount(p.a);
                if (count != mb.memberCount(p.b)) {
                    return false;
                }
                if (count > 0 && p.depth >= maxDepth) {
                    throw Evaluator.tooDeep(maxDepth);
                }
                for (Map.Entry<String, @Nullable Object> member : ma.members(p.a)) {
                    @Nullable Object other = mb.findMember(p.b, member.getKey());
                    if (other == null && !mb.hasMember(p.b, member.getKey())) {
                        return false;
                    }
                    pending.push(new Pending(member.getValue(), other, p.depth + 1));
                }
            } else if (!scalarEqual(kind, ma, p.a, mb, p.b)) {
                return false;
            }
        }
        return true;
    }

    /** Result of {@link #compareNumbers} when a number is not finite: neither less, equal nor greater. */
    private static final int INCOMPARABLE = 2;

    /** Compares two numbers as -1, 0 or 1, or returns {@link #INCOMPARABLE}. */
    private static int compareNumbers(
            JsonModel<@Nullable Object> ma, @Nullable Object a, JsonModel<@Nullable Object> mb, @Nullable Object b) {
        if (ma.isLong(a) && mb.isLong(b)) {
            return Long.compare(ma.longValue(a), mb.longValue(b));
        }
        BigDecimal na = ma.numberValue(a);
        BigDecimal nb = mb.numberValue(b);
        if (na == null || nb == null) {
            return INCOMPARABLE;
        }
        return Integer.signum(na.compareTo(nb));
    }

    private static boolean scalarEqual(
            JsonKind kind,
            JsonModel<@Nullable Object> ma,
            @Nullable Object a,
            JsonModel<@Nullable Object> mb,
            @Nullable Object b) {
        switch (kind) {
            case NULL:
                return true;
            case BOOLEAN:
                return ma.booleanValue(a) == mb.booleanValue(b);
            case NUMBER:
                return compareNumbers(ma, a, mb, b) == 0;
            case STRING:
                return ma.stringValue(a).equals(mb.stringValue(b));
            default:
                throw new IllegalStateException();
        }
    }
}
