package com.christophsens.jsonpath.internal;

import com.christophsens.jsonpath.JsonKind;
import com.christophsens.jsonpath.JsonModel;
import com.christophsens.jsonpath.internal.Ast.ComparisonOp;

/**
 * Comparison of values (RFC 9535, section 2.3.5.2.2).
 */
final class Values {

    private Values() {
    }

    static boolean compare(Val left, ComparisonOp op, Val right) {
        switch (op) {
            case EQ:
                return equal(left, right);
            case NE:
                return !equal(left, right);
            case LT:
                return less(left, right);
            case LE:
                return less(left, right) || equal(left, right);
            case GT:
                return less(right, left);
            case GE:
                return less(right, left) || equal(left, right);
            default:
                throw new IllegalStateException();
        }
    }

    private static boolean equal(Val a, Val b) {
        if (a.isNothing() || b.isNothing()) {
            return a.isNothing() && b.isNothing();
        }
        return deepEqual(a.model(), a.value(), b.model(), b.value());
    }

    private static boolean less(Val a, Val b) {
        if (a.isNothing() || b.isNothing()) {
            return false;
        }
        JsonKind ka = a.kind();
        JsonKind kb = b.kind();
        if (ka == JsonKind.NUMBER && kb == JsonKind.NUMBER) {
            return a.number().compareTo(b.number()) < 0;
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

    private static boolean deepEqual(JsonModel<Object> ma, Object a, JsonModel<Object> mb, Object b) {
        JsonKind kind = ma.kind(a);
        if (kind != mb.kind(b)) {
            return false;
        }
        switch (kind) {
            case NULL:
                return true;
            case BOOLEAN:
                return ma.booleanValue(a) == mb.booleanValue(b);
            case NUMBER:
                return ma.numberValue(a).compareTo(mb.numberValue(b)) == 0;
            case STRING:
                return ma.stringValue(a).equals(mb.stringValue(b));
            case ARRAY:
                int size = ma.size(a);
                if (size != mb.size(b)) {
                    return false;
                }
                for (int i = 0; i < size; i++) {
                    if (!deepEqual(ma, ma.element(a, i), mb, mb.element(b, i))) {
                        return false;
                    }
                }
                return true;
            case OBJECT:
                if (ma.memberCount(a) != mb.memberCount(b)) {
                    return false;
                }
                for (String name : ma.memberNames(a)) {
                    if (!mb.hasMember(b, name) || !deepEqual(ma, ma.member(a, name), mb, mb.member(b, name))) {
                        return false;
                    }
                }
                return true;
            default:
                throw new IllegalStateException();
        }
    }
}
