package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.FunctionValue;
import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A value of the RFC 9535 ValueType: a JSON value (in some model) or the special result Nothing. This is the
 * implementation of {@link FunctionValue}, so function arguments and results need no conversion.
 */
public final class Val implements FunctionValue {

    public static final Val NOTHING = new Val(null, null);

    private final @Nullable Object value;
    private final @Nullable JsonModel<@Nullable Object> model;

    private Val(@Nullable Object value, @Nullable JsonModel<@Nullable Object> model) {
        this.value = value;
        this.model = model;
    }

    public static Val of(@Nullable Object value, JsonModel<@Nullable Object> model) {
        return new Val(value, model);
    }

    /** A value in the {@link JavaObjectModel}, used for literals and computed results. */
    public static Val literal(@Nullable Object value) {
        return new Val(value, JavaObjectModel.INSTANCE);
    }

    /**
     * Returns a function result as a Val. Values from {@link FunctionValue}'s factories and from the arguments
     * are Vals already; another implementation is converted by its kind, for scalars only.
     *
     * @throws NullPointerException if a function returned null
     * @throws IllegalArgumentException for another implementation of an array or object
     */
    public static Val from(FunctionValue value, String function) {
        if (value instanceof Val val) {
            return val;
        }
        if (value == null) {
            throw new NullPointerException(
                    "Function '" + function + "' returned null; return FunctionValue.nothing() for no value");
        }
        if (value.isNothing()) {
            return NOTHING;
        }
        return switch (value.kind()) {
            case STRING -> literal(value.string());
            case NUMBER -> literal(value.number());
            case BOOLEAN -> literal(value.bool());
            case NULL -> literal(null);
            case ARRAY, OBJECT ->
                throw new IllegalArgumentException(
                        "Function '" + function
                                + "' returned an array or object that is not one of its arguments; functions cannot create them");
        };
    }

    @Override
    public boolean isNothing() {
        return model == null;
    }

    @Override
    public JsonKind kind() {
        return model().kind(value);
    }

    public @Nullable Object value() {
        return value;
    }

    /** The model of the value; Nothing has none. */
    public JsonModel<@Nullable Object> model() {
        if (model == null) {
            throw new IllegalStateException("Nothing has no value");
        }
        return model;
    }

    @Override
    public String string() {
        require(JsonKind.STRING);
        return model().stringValue(value);
    }

    @Override
    public BigDecimal number() {
        require(JsonKind.NUMBER);
        BigDecimal number = model().numberValue(value);
        if (number == null) {
            throw new IllegalStateException("Not a finite number");
        }
        return number;
    }

    @Override
    public boolean bool() {
        require(JsonKind.BOOLEAN);
        return model().booleanValue(value);
    }

    @Override
    public int size() {
        JsonKind kind = kind();
        if (kind == JsonKind.ARRAY) {
            return model().size(value);
        }
        if (kind == JsonKind.OBJECT) {
            return model().memberCount(value);
        }
        throw new IllegalStateException("Not an array or object but " + kind);
    }

    @Override
    public FunctionValue element(int index) {
        require(JsonKind.ARRAY);
        if (index < 0 || index >= model().size(value)) {
            throw new IndexOutOfBoundsException(index);
        }
        return of(model().element(value, index), model());
    }

    @Override
    public List<String> memberNames() {
        require(JsonKind.OBJECT);
        List<String> names = new ArrayList<>();
        model().memberNames(value).forEach(names::add);
        return List.copyOf(names);
    }

    @Override
    public FunctionValue member(String name) {
        require(JsonKind.OBJECT);
        Object member = model().findMember(value, name);
        if (member == null && !model().hasMember(value, name)) {
            return NOTHING;
        }
        return of(member, model());
    }

    private void require(JsonKind expected) {
        JsonKind kind = kind();
        if (kind != expected) {
            throw new IllegalStateException("Not " + expected + " but " + kind);
        }
    }

    @Override
    public String toString() {
        return isNothing() ? "Nothing" : kind() + " " + value;
    }
}
