package com.christophsens.jsonpath.migration;

import com.christophsens.jsonpath.migration.Comparison.Outcome;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The comparisons for a set of expressions, with a readable summary ({@link #toString()}).
 */
public final class MigrationReport {

    private final List<Comparison> comparisons;

    MigrationReport(List<Comparison> comparisons) {
        this.comparisons = Collections.unmodifiableList(new ArrayList<>(comparisons));
    }

    /** Returns all comparisons in input order. */
    public List<Comparison> comparisons() {
        return comparisons;
    }

    /** Returns the comparisons that need attention: everything except {@link Outcome#SAME}. */
    public List<Comparison> differences() {
        List<Comparison> differences = new ArrayList<>();
        for (Comparison comparison : comparisons) {
            if (!comparison.isSame()) {
                differences.add(comparison);
            }
        }
        return Collections.unmodifiableList(differences);
    }

    /** Returns the number of comparisons per outcome. */
    public Map<Outcome, Integer> counts() {
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (Comparison comparison : comparisons) {
            counts.merge(comparison.outcome(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(comparisons.size()).append(" expressions compared with Jayway JsonPath: ");
        sb.append(counts()).append('\n');
        for (Comparison comparison : comparisons) {
            if (!comparison.isSame() || comparison.jaywayReturnsSingleValue()) {
                sb.append('\n').append(comparison).append('\n');
            }
        }
        return sb.toString();
    }
}
