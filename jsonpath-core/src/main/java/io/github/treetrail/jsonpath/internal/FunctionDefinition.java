package io.github.treetrail.jsonpath.internal;

import io.github.treetrail.jsonpath.FunctionArguments;
import io.github.treetrail.jsonpath.FunctionType;
import io.github.treetrail.jsonpath.FunctionValue;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A function (RFC 9535, section 2.4): its name, declared parameter types and implementation. The kind of
 * implementation is the declared result type. Built-in functions and function extensions
 * ({@link io.github.treetrail.jsonpath.FunctionExtension}) are both declared this way, with bodies written
 * against {@link FunctionArguments} and {@link FunctionValue}.
 *
 * <p>The parser checks every call against the declared types (section 2.4.3), so a body reads its arguments
 * with the accessor of the declared type and needs no checks of its own.
 */
public record FunctionDefinition(String name, List<FunctionType> parameters, Implementation implementation) {

    /** The body of a function; {@link ValueFunction}, {@link LogicalFunction} or {@link NodesFunction}. */
    public sealed interface Implementation permits ValueFunction, LogicalFunction, NodesFunction {}

    /** A function of ValueType: returns a value or {@link FunctionValue#nothing()}. */
    public record ValueFunction(Function<FunctionArguments, FunctionValue> body) implements Implementation {}

    /** A function of LogicalType. */
    public record LogicalFunction(Predicate<FunctionArguments> body) implements Implementation {}

    /** A function of NodesType: returns the values of the nodes it selects. */
    public record NodesFunction(Function<FunctionArguments, List<FunctionValue>> body) implements Implementation {}

    /** Returns the declared result type, given by the kind of implementation. */
    public FunctionType result() {
        if (implementation instanceof ValueFunction) {
            return FunctionType.VALUE;
        }
        return implementation instanceof LogicalFunction ? FunctionType.LOGICAL : FunctionType.NODES;
    }

    /**
     * The arguments of one call, in the order of the parameters: a {@link Val} for ValueType, a
     * {@link Boolean} for LogicalType and a {@code List<Val>} for NodesType.
     */
    public static final class Arguments implements FunctionArguments {

        private final Object[] values;

        Arguments(Object[] values) {
            this.values = values;
        }

        @Override
        public int size() {
            return values.length;
        }

        @Override
        public Val value(int index) {
            return (Val) values[index];
        }

        @Override
        public boolean logical(int index) {
            return (Boolean) values[index];
        }

        @Override
        public List<FunctionValue> nodes(int index) {
            return Collections.unmodifiableList(vals(index));
        }

        /** The values of a NodesType argument as {@link Val}s. */
        @SuppressWarnings("unchecked")
        public List<Val> vals(int index) {
            return (List<Val>) values[index];
        }
    }
}
