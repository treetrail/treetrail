package io.github.treetrail.jsonpath.benchmarks;

/**
 * The benchmarked queries, written so that both libraries accept them and select the same nodes.
 */
final class Queries {

    private Queries() {}

    /** The expression for this library. */
    static String rfc9535(String query) {
        return switch (query) {
            case "definite" -> "$.store.bicycle.color";
            case "wildcard" -> "$.store.book[*].title";
            case "filter" -> "$.store.book[?(@.price < 10 && @.category == 'fiction')].title";
            case "descendant" -> "$..price";
            case "regex" -> "$.store.book[?match(@.author, 'H.*')].title";
            default -> throw new IllegalArgumentException(query);
        };
    }

    /** The expression for Jayway JsonPath: the same, except for the regular expression syntax. */
    static String jayway(String query) {
        return query.equals("regex") ? "$.store.book[?(@.author =~ /H.*/)].title" : rfc9535(query);
    }
}
