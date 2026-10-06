# Treetrail

JSONPath for Java that behaves the same everywhere: an implementation of
[RFC 9535](https://www.rfc-editor.org/rfc/rfc9535), the IETF standard for JSONPath.

- **Standard-conformant:** passes all 706 cases of the
  [JSONPath Compliance Test Suite](https://github.com/jsonpath-standard/jsonpath-compliance-test-suite)
  (commit `9d1a415`, 2026-09-17). The suite runs on every build; a single failing case fails CI.
- **No dependencies:** the core depends on nothing but the JDK. It works on any JSON tree through
  a small `JsonModel` interface, so documents from any JSON library can be queried without conversion.
- **Predictable results:** a query always returns a node list, and every node carries its
  normalized path, for example `$['store']['book'][0]`.
- **No catastrophic backtracking:** regular expressions in `match()` and `search()` follow
  [I-Regexp (RFC 9485)](https://www.rfc-editor.org/rfc/rfc9485) and run on a built-in automaton
  without backtracking, so matching takes time linear in the input length. A pattern like
  `(a*)*b` on 100,000 characters finishes in milliseconds instead of hanging.
  Expression nesting and regex size are limited.
- **Bounded work:** absolute queries inside filters are evaluated once per run, and every run has a
  budget of visited nodes, so a hostile query cannot keep a thread busy indefinitely; see
  [Queries from untrusted sources](#queries-from-untrusted-sources).
- **Fast:** on par with or faster than Jayway JsonPath in every query benchmark, up to 1.8× for
  wildcards; see [docs/benchmarks.md](docs/benchmarks.md).
- **Java 17+**, a named JPMS module (`io.github.treetrail.jsonpath`).

> **Status:** pre-release, not yet published to Maven Central. The API may still change.
> Coordinates will be `io.github.treetrail:jsonpath-core` (plus `jsonpath-jackson2`, `jsonpath-jackson3`,
> `jsonpath-gson`, `jsonpath-jsonp`, `jsonpath-migration`, `jsonpath-rewrite`).

## Usage

```java
JsonPath path = JsonPath.compile("$.store.book[?@.price < 10].title");

NodeList<Object> nodes = path.query(document);
nodes.values(); // ["Sayings of the Century", "Moby Dick"]
nodes.paths();  // ["$['store']['book'][0]['title']", "$['store']['book'][2]['title']"]
```

`query(Object)` works on plain Java objects: `Map` for objects, `List` for arrays, `String`,
`Number`, `Boolean` and `null`. That is what most JSON libraries produce when asked for untyped
output, for example Jackson's `objectMapper.readValue(json, Object.class)`.

### Jackson, Gson and other JSON libraries

Adapters let you query a library's own tree directly, without converting it. The query returns
the original nodes, so you can keep working with them.

| Module | Tree type | Model |
| --- | --- | --- |
| `jsonpath-jackson2` | `com.fasterxml.jackson.databind.JsonNode` | `Jackson2Model.INSTANCE` |
| `jsonpath-jackson3` | `tools.jackson.databind.JsonNode` | `Jackson3Model.INSTANCE` |
| `jsonpath-gson` | `com.google.gson.JsonElement` | `GsonModel.INSTANCE` |
| `jsonpath-jsonp` | `jakarta.json.JsonValue` (any JSON-P implementation) | `JsonpModel.INSTANCE` |

```java
JsonNode document = objectMapper.readTree(json);
NodeList<JsonNode> books = JsonPath.compile("$.store.book[?@.price < 10]")
        .query(document, Jackson2Model.INSTANCE);
```

Each adapter passes the full Compliance Test Suite with documents parsed by its own library at
default settings. For other object models, implement the ten abstract methods of `JsonModel<N>` and
call `query(document, model)`; override its default methods, such as `members()`, where your library
can answer faster.

Compiled queries are immutable and thread-safe. Compile once, reuse often.
Invalid queries throw a `JsonPathSyntaxException` with the position of the problem and an excerpt of
the expression. If the document contains a value the model rejects, the query throws a
`JsonPathEvaluationException` with the normalized path of the node and the original exception as cause.

### Queries from untrusted sources

A query needs time and memory roughly in proportion to the nodes it visits, so every run counts
them: each node a selector produces, each node a descendant segment walks through and each node a
filter tests. Absolute queries inside filters, such as `$.limit` in `$.items[?@.price < $.limit]`,
are evaluated once per run, so nesting them does not multiply the work.

By default a run may visit 100,000,000 nodes, which took 1.8 seconds on the benchmark machine, and
walk or compare at most 1,000 levels deep, which also stops plain Java maps that contain themselves.
For queries from untrusted sources, set limits that fit your documents:

```java
JsonPath path = JsonPath.compile(untrustedExpression)
        .withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(1_000_000).withMaxResultSize(10_000));
```

A run that exceeds a limit throws a `JsonPathLimitExceededException`. A run on an interrupted thread
stops with a `JsonPathEvaluationException` and leaves the interrupt status set, so `Future.cancel(true)`
can bound a query by time. Compiling is bounded as well: filters nest at most 64 levels deep, and
regular expressions are limited in size (see [Design notes](#design-notes)).

## Coming from Jayway JsonPath

RFC 9535 standardizes JSONPath but differs from Jayway JsonPath in several places, for example:

| | Jayway JsonPath | RFC 9535 |
| --- | --- | --- |
| Result of `$.a.b` | a single value or a list, depending on the path and configuration | always a node list |
| Filters | `[?(@.price < 10)]` | `[?@.price < 10]` (parentheses optional) |
| Regular expressions | `=~ /regex/` with Java regex | `match()` and `search()` with I-Regexp |
| Functions | `min()`, `max()`, `sum()`, `avg()`, `length()`, ... at the end of a path | `length()`, `count()`, `match()`, `search()`, `value()` inside filters |
| Operators | `in`, `nin`, `subsetof`, `size`, `empty`, ... | `==`, `!=`, `<`, `<=`, `>`, `>=`, `&&`, `\|\|`, `!` |
| Write API | `set`, `put`, `add`, `delete` | queries only |

### Checking your expressions before you switch

The `jsonpath-migration` module runs your expressions with Jayway JsonPath and with this library
and reports every difference, with hints for rewriting Jayway-only syntax. Pass your application's
Jayway `Configuration` so that options like `DEFAULT_PATH_LEAF_TO_NULL` are taken into account.

```java
MigrationReport report = JaywayComparison.with(applicationConfiguration)
        .compareAllJson(List.of("$.store.book[?(@.price < 10)].title", "$.store.book.length()"), sampleJson);
System.out.println(report);
assertThat(report.differences()).isEmpty();
```

Each expression gets one outcome: `SAME`, `SAME_VALUES_DIFFERENT_ORDER`, `DIFFERENT_VALUES`,
`ONLY_JAYWAY_ACCEPTS` (with a rewrite hint), `ONLY_RFC_ACCEPTS`, `JAYWAY_FAILS_AT_RUNTIME` or
`BOTH_REJECT`. The report also flags paths where Jayway returns a single value instead of a list.

[docs/jayway-vs-rfc9535.md](docs/jayway-vs-rfc9535.md) compares Jayway JsonPath 3.0.0 with the
Compliance Test Suite case by case (`./gradlew jaywayReport` regenerates it).

### Finding every expression in a code base

The `jsonpath-rewrite` module contains the [OpenRewrite](https://docs.openrewrite.org) recipe
`io.github.treetrail.jsonpath.rewrite.FindJaywayJsonPathExpressions`. It finds expressions passed to
Jayway JsonPath (`JsonPath.read`, `JsonPath.compile`, `ReadContext.read`, the write API) and to
Spring's `MockMvcResultMatchers.jsonPath`, marks each call site with an assessment and fills the data
table `JsonPathExpressions` with one row per expression:

| Assessment | Meaning |
| --- | --- |
| `VALID` | Valid RFC 9535; verify results with `JaywayComparison` |
| `VALID_SINGLE_VALUE` | Valid RFC 9535, but Jayway returns a single value here: use `NodeList.single()` |
| `NOT_RFC_9535` | Jayway-only syntax, with a rewrite hint |
| `WRITE_API` | `set`, `put`, `delete`, ...: RFC 9535 defines queries only |
| `NOT_A_LITERAL` | Computed at runtime; check with `JaywayComparison` |

The recipe changes no code: whether a call can be migrated automatically depends on how its result
is used.

## Design notes

- I-Regexp lists `^` and `$` as ordinary characters, but the Compliance Test Suite expects them to
  act as anchors. This implementation follows the test suite.
- Regular expressions are compiled into a nondeterministic automaton (Thompson's construction)
  and simulated with a set of states, like RE2 or Rust's `regex`. Expressions that would expand to
  more than 20,000 automaton instructions, for example `(a{1000}){1000}`, count as invalid, so
  `match()` and `search()` return false for them; RFC 9485 allows such limits.
- Numbers are compared by value: `1`, `1L`, `1.0` and `new BigDecimal("1.00")` are equal.
- Strings are compared by Unicode code points, as the RFC requires (not by UTF-16 code units).

## Building

```bash
./gradlew build
```

The build compiles with JDK 25 for Java 17 and runs the unit tests and the compliance suite on
Java 17, 21 and 25. Gradle downloads the JDKs it does not find.

## License

[Apache License 2.0](LICENSE). The vendored Compliance Test Suite (test sources only) is licensed
under BSD-2, see [NOTICE](NOTICE).
