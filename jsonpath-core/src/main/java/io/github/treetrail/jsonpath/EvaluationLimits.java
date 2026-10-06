package io.github.treetrail.jsonpath;

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
 * time and memory a query needs. A query that exceeds a limit fails with a
 * {@link JsonPathLimitExceededException}.
 *
 * <p>Instances are immutable.
 */
public final class EvaluationLimits {

    /**
     * The limits every query starts with: at most 100,000,000 visited nodes, which bounds a query to a
     * few seconds of work, and no limit on the result size.
     */
    public static final EvaluationLimits DEFAULT = new EvaluationLimits(100_000_000L, Integer.MAX_VALUE);

    /** No limits. Only use this for queries from trusted sources. */
    public static final EvaluationLimits NONE = new EvaluationLimits(Long.MAX_VALUE, Integer.MAX_VALUE);

    private final long maxVisitedNodes;
    private final int maxResultSize;

    private EvaluationLimits(long maxVisitedNodes, int maxResultSize) {
        this.maxVisitedNodes = maxVisitedNodes;
        this.maxResultSize = maxResultSize;
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
        return new EvaluationLimits(max, maxResultSize);
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
        return new EvaluationLimits(maxVisitedNodes, max);
    }

    /** Returns the maximum number of node visits. */
    public long maxVisitedNodes() {
        return maxVisitedNodes;
    }

    /** Returns the maximum number of nodes in a query result. */
    public int maxResultSize() {
        return maxResultSize;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EvaluationLimits)) {
            return false;
        }
        EvaluationLimits other = (EvaluationLimits) o;
        return maxVisitedNodes == other.maxVisitedNodes && maxResultSize == other.maxResultSize;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(maxVisitedNodes) + maxResultSize;
    }

    @Override
    public String toString() {
        return "EvaluationLimits[maxVisitedNodes=" + maxVisitedNodes + ", maxResultSize=" + maxResultSize + "]";
    }
}
