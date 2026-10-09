package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A value of the RFC 9535 ValueType: a JSON value (in some model) or the special result Nothing.
 */
public final class Val {

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

    public boolean isNothing() {
        return model == null;
    }

    public JsonKind kind() {
        return model().kind(value);
    }

    public @Nullable Object value() {
        return value;
    }

    /** The model of the value; Nothing has none. */
    public JsonModel<@Nullable Object> model() {
        return Objects.requireNonNull(model, "Nothing has no model");
    }

    public String string() {
        return model().stringValue(value);
    }
}
