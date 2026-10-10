package io.github.treetrail.jsonpath.internal;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A function extension (RFC 9535, section 2.4): its name, declared parameter types and implementation. The
 * kind of implementation is the declared result type.
 *
 * <p>The parser checks every call against the declared types (section 2.4.3), so a body reads its arguments
 * through {@link Arguments} with the accessor of the declared type and needs no checks of its own.
 */
public record FunctionDefinition(String name, List<Type> parameters, Implementation implementation) {

    /** The RFC 9535 function expression types. */
    public enum Type {
        VALUE,
        LOGICAL,
        NODES
    }

    /** The body of a function; {@link ValueFunction}, {@link LogicalFunction} or {@link NodesFunction}. */
    public sealed interface Implementation permits ValueFunction, LogicalFunction, NodesFunction {}

    /** A function of ValueType: returns a value or {@link Val#NOTHING}. */
    public record ValueFunction(Function<Arguments, Val> body) implements Implementation {}

    /** A function of LogicalType. */
    public record LogicalFunction(Predicate<Arguments> body) implements Implementation {}

    /** A function of NodesType: returns the values of the nodes it selects. */
    public record NodesFunction(Function<Arguments, List<Val>> body) implements Implementation {}

    /** Returns the declared result type, given by the kind of implementation. */
    public Type result() {
        if (implementation instanceof ValueFunction) {
            return Type.VALUE;
        }
        return implementation instanceof LogicalFunction ? Type.LOGICAL : Type.NODES;
    }

    /**
     * The arguments of one call, in the order of the parameters. Each argument is read with the accessor of
     * its declared type: {@link #value} for ValueType, {@link #logical} for LogicalType and {@link #nodes}
     * for NodesType, the values of the selected nodes.
     */
    public static final class Arguments {

        private final Object[] values;

        Arguments(Object[] values) {
            this.values = values;
        }

        public Val value(int index) {
            return (Val) values[index];
        }

        public boolean logical(int index) {
            return (Boolean) values[index];
        }

        @SuppressWarnings("unchecked")
        public List<Val> nodes(int index) {
            return (List<Val>) values[index];
        }
    }
}
