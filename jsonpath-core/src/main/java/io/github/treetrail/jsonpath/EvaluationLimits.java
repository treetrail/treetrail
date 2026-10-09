package io.github.treetrail.jsonpath;

import org.jspecify.annotations.Nullable;

/**
 * Limits for running a query, to bound the work spent on queries from untrusted sources.
 *
 * <pre>{@code
 * JsonPath path = JsonPath.compile(untrustedExpression)
 *         .withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(1_000_000).withMaxResultSize(10_000));
 * }</pre>
 *
 * <p>The evaluation counts every visit of a node: each node a selector produces, each node a
 * descendant segment walks through and each node a filter tests. A node visited several times, for
 * example by nested descendant segments, counts each time. The count is roughly proportional to the
 * time and memory a query needs.
 *
 * <p>The nesting depth limits how deep a query walks into the document and how deep two values are
 * compared. It also stops queries on plain Java objects that contain themselves, which would otherwise
 * be infinitely deep.
 *
 * <p>A query that exceeds a limit fails with a {@link JsonPathLimitExceededException}.
 *
 * <p>Instances are immutable.
 */
public final class EvaluationLimits {

    /**
     * The limits every query starts with: at most 100,000,000 visited nodes, which bounds a query to a
     * few seconds of work, a nesting depth of 1,000 (the default of Jackson's parser) and no limit on the
     * result size.
     */
    public static final EvaluationLimits DEFAULT = new EvaluationLimits(100_000_000L, Integer.MAX_VALUE, 1_000);

    /**
     * No limits. Only use this for queries from trusted sources and documents without cycles. Values are
     * compared without recursion, so deep documents cannot overflow the stack.
     */
    public static final EvaluationLimits NONE =
            new EvaluationLimits(Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final long maxVisitedNodes;
    private final int maxResultSize;
    private final int maxDepth;

    private EvaluationLimits(long maxVisitedNodes, int maxResultSize, int maxDepth) {
        this.maxVisitedNodes = maxVisitedNodes;
        this.maxResultSize = maxResultSize;
        this.maxDepth = maxDepth;
    }

    /**
     * Returns limits with the given maximum number of node visits.
     *
     * @throws IllegalArgumentException if {@code max} is negative
     */
    public EvaluationLimits withMaxVisitedNodes(long max) {
        if (max < 0) {
            throw new IllegalArgumentException("Maximum number of visited nodes must not be negative: " + max);
        }
        return new EvaluationLimits(max, maxResultSize, maxDepth);
    }

    /**
     * Returns limits with the given maximum number of nodes in a query result.
     *
     * @throws IllegalArgumentException if {@code max} is negative
     */
    public EvaluationLimits withMaxResultSize(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("Maximum result size must not be negative: " + max);
        }
        return new EvaluationLimits(maxVisitedNodes, max, maxDepth);
    }

    /**
     * Returns limits with the given maximum nesting depth. The root of the document has depth 0, its
     * children depth 1.
     *
     * @throws IllegalArgumentException if {@code max} is negative
     */
    public EvaluationLimits withMaxDepth(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("Maximum depth must not be negative: " + max);
        }
        return new EvaluationLimits(maxVisitedNodes, maxResultSize, max);
    }

    /** Returns the maximum number of node visits. */
    public long maxVisitedNodes() {
        return maxVisitedNodes;
    }

    /** Returns the maximum number of nodes in a query result. */
    public int maxResultSize() {
        return maxResultSize;
    }

    /** Returns the maximum nesting depth. */
    public int maxDepth() {
        return maxDepth;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof EvaluationLimits other)) {
            return false;
        }

        return maxVisitedNodes == other.maxVisitedNodes
                && maxResultSize == other.maxResultSize
                && maxDepth == other.maxDepth;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Long.hashCode(maxVisitedNodes) + maxResultSize) + maxDepth;
    }

    @Override
    public String toString() {
        return "EvaluationLimits[maxVisitedNodes=" + maxVisitedNodes + ", maxResultSize=" + maxResultSize
                + ", maxDepth=" + maxDepth + "]";
    }
}
