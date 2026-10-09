package io.github.treetrail.jsonpath.migration;

import io.github.treetrail.jsonpath.migration.Comparison.Outcome;
import io.github.treetrail.jsonpath.testing.ComplianceSuite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs the JSONPath Compliance Test Suite through Jayway JsonPath (default configuration) and writes
 * {@code build/reports/jayway-<version>-vs-rfc9535.md}. Not a regression test: run with
 * {@code ./gradlew jaywayReport} (Jayway 3.x) or {@code ./gradlew jaywayReport2} (Jayway 2.x).
 *
 * <p>Valid test cases are judged against the suite's expected results, so the report does not depend
 * on this library. Where the suite allows several orders, any of them counts as correct.
 */
@Tag("report")
class JaywayConformanceReport {

    @Test
    void writeReport() throws IOException {
        String version = System.getProperty("jayway.version");
        StringBuilder md = new StringBuilder();
        md.append("# Jayway JsonPath ").append(version).append(" vs. RFC 9535\n\n");
        md.append("JSONPath Compliance Test Suite, Jayway default configuration; Jayway parses each document\n");
        md.append("itself (json-smart). Valid queries are judged\n");
        md.append("against the suite's expected results.\n\n");
        md.append("RFC 9535 allows filters without parentheses (`[?@.a]`); Jayway requires `[?(@.a)]`.\n");
        md.append("Part 2 therefore repeats the run with parentheses added around every filter, so that it\n");
        md.append("measures behaviour rather than this one difference in notation.\n");
        md.append("\n# Part 1: queries as written\n\n");
        section(md, false);
        md.append("\n# Part 2: filters wrapped in parentheses for Jayway\n\n");
        section(md, true);
        Path out = Path.of("build", "reports", "jayway-" + version + "-vs-rfc9535.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static void section(StringBuilder md, boolean parenthesize) {
        JaywayComparison comparison = JaywayComparison.withDefaults();
        Map<Outcome, List<String>> valid = new EnumMap<>(Outcome.class);
        int validCount = 0;
        int invalidCount = 0;
        List<String> invalidAccepted = new ArrayList<>();

        for (Map<String, Object> test : ComplianceSuite.cases()) {
            String selector = (String) test.get("selector");
            if (parenthesize) {
                selector = withFilterParentheses(selector);
            }
            if (Boolean.TRUE.equals(test.get("invalid_selector"))) {
                invalidCount++;
                if (jaywayAccepts(selector)) {
                    invalidAccepted.add(row(test, "accepted", null));
                }
                continue;
            }
            validCount++;
            Comparison c = comparison.compareJson(selector, json(test.get("document")));
            if (c.outcome() == Outcome.ONLY_JAYWAY_ACCEPTS || c.outcome() == Outcome.BOTH_REJECT) {
                // The parenthesized form is always valid RFC 9535; only Jayway's view matters here.
                c = new Comparison(
                        selector,
                        c.jaywayValues() == null ? Outcome.ONLY_RFC_ACCEPTS : Outcome.DIFFERENT_VALUES,
                        null,
                        c.jaywayValues(),
                        false,
                        c.detail());
            }
            Outcome outcome = c.outcome();
            if (c.jaywayValues() != null && matchesExpected(c.jaywayValues(), test)) {
                outcome = Outcome.SAME;
            } else if (outcome == Outcome.SAME) {
                outcome = Outcome.DIFFERENT_VALUES;
            }
            String expected = String.valueOf(
                    test.containsKey("result") ? test.get("result") : ((List<Object>) test.get("results")).get(0));
            valid.computeIfAbsent(outcome, k -> new ArrayList<>()).add(row(test, expected, c));
        }

        md.append("## Valid RFC 9535 queries: ").append(validCount).append("\n\n");
        md.append("| Outcome | Cases |\n| --- | --- |\n");
        for (Outcome outcome : Outcome.values()) {
            if (valid.containsKey(outcome)) {
                md.append("| ")
                        .append(outcome)
                        .append(" | ")
                        .append(valid.get(outcome).size())
                        .append(" |\n");
            }
        }
        md.append("\n## Invalid RFC 9535 queries: ").append(invalidCount).append("\n\n");
        md.append("Jayway accepts ").append(invalidAccepted.size()).append(" of them.\n");
        for (Outcome outcome : Outcome.values()) {
            if (outcome != Outcome.SAME && valid.containsKey(outcome)) {
                md.append("\n## ").append(outcome).append("\n\n");
                md.append("| Test | Selector | Expected (RFC 9535) | Jayway |\n| --- | --- | --- | --- |\n");
                valid.get(outcome).forEach(md::append);
            }
        }
        md.append("\n## Invalid queries Jayway accepts\n\n");
        md.append("| Test | Selector |\n| --- | --- |\n");
        for (String r : invalidAccepted) {
            md.append(r);
        }
    }

    /** Wraps the expression of every filter selector in parentheses: {@code [?@.a]} becomes {@code [?(@.a)]}. */
    static String withFilterParentheses(String selector) {
        StringBuilder out = new StringBuilder();
        java.util.Deque<Character> open = new java.util.ArrayDeque<>();
        java.util.Deque<Integer> filterDepths = new java.util.ArrayDeque<>();
        int i = 0;
        while (i < selector.length()) {
            char c = selector.charAt(i);
            if (c == '\'' || c == '"') {
                int end = i + 1;
                while (end < selector.length() && selector.charAt(end) != c) {
                    end += selector.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(end + 1, selector.length());
                out.append(selector, i, end);
                i = end;
                continue;
            }
            if (c == '[' || c == '(') {
                open.push(c);
            } else if (c == ']' || c == ')') {
                if (c == ']' && !filterDepths.isEmpty() && filterDepths.peek() == open.size()) {
                    out.append(')');
                    filterDepths.pop();
                }
                open.poll();
            } else if (c == ',' && !filterDepths.isEmpty() && filterDepths.peek() == open.size()) {
                out.append(')');
                filterDepths.pop();
            }
            out.append(c);
            if (c == '?' && !open.isEmpty() && open.peek() == '[') {
                out.append('(');
                filterDepths.push(open.size());
            }
            i++;
        }
        return out.toString();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static String json(Object document) {
        try {
            return MAPPER.writeValueAsString(document);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static boolean jaywayAccepts(String selector) {
        try {
            com.jayway.jsonpath.JsonPath.compile(selector);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean matchesExpected(List<Object> jayway, Map<String, Object> test) {
        if (test.containsKey("result")) {
            return JaywayComparison.jsonEquals(jayway, test.get("result"));
        }
        for (Object alternative : (List<Object>) test.get("results")) {
            if (JaywayComparison.jsonEquals(jayway, alternative)) {
                return true;
            }
        }
        return false;
    }

    private static String row(Map<String, Object> test, String expected, Comparison c) {
        String jayway = c == null
                ? ""
                : c.jaywayValues() != null ? String.valueOf(c.jaywayValues()) : String.valueOf(c.detail());
        if (c == null) {
            return "| " + cell(test.get("name")) + " | `" + cell(test.get("selector")) + "` |\n";
        }
        return "| " + cell(test.get("name")) + " | `" + cell(test.get("selector")) + "` | " + cell(expected) + " | "
                + cell(jayway) + " |\n";
    }

    private static String cell(Object value) {
        String s = String.valueOf(value)
                .replace("|", "\\|")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}
