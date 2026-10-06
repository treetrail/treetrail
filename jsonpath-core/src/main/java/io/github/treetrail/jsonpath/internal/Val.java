package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;

/**
 * A value of the RFC 9535 ValueType: a JSON value (in some model) or the special result Nothing.
 */
public final class Val {

    public static final Val NOTHING = new Val(null, null);

    private final Object value;
    private final JsonModel<Object> model;

    private Val(Object value, JsonModel<Object> model) {
        this.value = value;
        this.model = model;
    }

    public static Val of(Object value, JsonModel<Object> model) {
        return new Val(value, model);
    }

    /** A value in the {@link JavaObjectModel}, used for literals and computed results. */
    public static Val literal(Object value) {
        return new Val(value, JavaObjectModel.INSTANCE);
    }

    public boolean isNothing() {
        return model == null;
    }

    public JsonKind kind() {
        return model.kind(value);
    }

    public Object value() {
        return value;
    }

    public JsonModel<Object> model() {
        return model;
    }

    public String string() {
        return model.stringValue(value);
    }
}
