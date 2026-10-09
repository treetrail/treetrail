# Treetrail

JSONPath queries for Java that return the same results as other standard implementations, in any
language: Treetrail implements [RFC 9535](https://www.rfc-editor.org/rfc/rfc9535), the IETF standard
for JSONPath.

- **One kind of result:** every query returns a list of nodes, whether it selects one value or many,
  and every node carries its normalized path, for example `$['store']['book'][0]`. There is no
  configuration that changes what a query returns.
- **Works on the JSON tree you already have:** Jackson 2 and 3, Gson, JSON-P or plain Java objects,
  without conversion. The core depends on nothing but the JDK; other object models plug in through
  the small `JsonModel` interface.
- **Safe with untrusted queries:** regular expressions in `match()` and `search()` follow
  [I-Regexp (RFC 9485)](https://www.rfc-editor.org/rfc/rfc9485) and run on an automaton without
  backtracking, in time linear in the input: `(a*)*b` on 100,000 characters finishes in milliseconds.
  Every run has a budget of visited nodes and a depth limit; see
  [Queries from untrusted sources](#queries-from-untrusted-sources).
- **A way off Jayway JsonPath:** a comparison module reports every expression whose result would
  change, and an OpenRewrite recipe finds every expression in a code base; see
  [Coming from Jayway JsonPath](#coming-from-jayway-jsonpath).
- **Standard-conformant:** passes all 706 cases of the
  [JSONPath Compliance Test Suite](https://github.com/jsonpath-standard/jsonpath-compliance-test-suite)
  (commit `9d1a415`, 2026-09-17) on Java 17, 21 and 25; a single failing case fails the build.
  [docs/conformance.md](docs/conformance.md) lists deviations, decisions and limits.
- **Fast:** on par with or faster than Jayway JsonPath in every query benchmark: on plain Java objects
  up to 1.8× for wildcards, on Jackson trees 1.2× to 2.2× faster in every query; see
  [docs/benchmarks.md](docs/benchmarks.md).
- **Java 17+**, a named JPMS module (`io.github.treetrail.jsonpath`), with [JSpecify](https://jspecify.dev)
  nullness annotations, so Kotlin sees which values can be `null`.

## Installation

Treetrail is on Maven Central. The API may still change before 1.0.

```kotlin
dependencies {
    implementation("io.github.treetrail:jsonpath-core:0.2.0")
    // or an adapter, which brings jsonpath-core along:
    implementation("io.github.treetrail:jsonpath-jackson2:0.2.0")
}
```

```xml
<dependency>
    <groupId>io.github.treetrail</groupId>
    <artifactId>jsonpath-core</artifactId>
    <version>0.2.0</version>
</dependency>
```

Modules: `jsonpath-core`, `jsonpath-jackson2`, `jsonpath-jackson3`, `jsonpath-gson`, `jsonpath-jsonp`,
`jsonpath-assertj`, `jsonpath-spring-test`, `jsonpath-migration` and `jsonpath-rewrite`. Every release is
signed, ships a CycloneDX SBOM per module and has a build provenance attestation; see
[docs/releasing.md](docs/releasing.md#what-a-release-contains). Changes are listed in
[CHANGELOG.md](CHANGELOG.md).

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

JSON text needs no JSON library: `queryJson` parses it with a small, strict parser built into the core
(`JavaObjectModel.parse`), which keeps exact numbers and rejects duplicate member names.

```java
List<Object> titles = JsonPath.compile("$.store.book[*].title").queryJson(responseBody).values();
```

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
regular expressions are limited in size. Regular expressions may also come from the document, as in
`match(@.value, @.pattern)`; the caches for them hold about 10 MB at most, however many different
expressions a document contains (see [docs/conformance.md](docs/conformance.md#limits)).

### Testing with AssertJ and Spring

`jsonpath-assertj` adds AssertJ assertions on JSON text. Values compare as JSON values, so `399`,
`399L` and `399.0` all match a JSON number `399`, and objects match regardless of member order.

```java
import static io.github.treetrail.jsonpath.assertj.JsonPathAssertions.assertThatJson;

assertThatJson(body).jsonPath("$.store.book[?@.price < 10].title").containsExactly("Sayings", "Moby Dick");
assertThatJson(body).jsonPath("$.store.bicycle.price").singleValue().isEqualTo(399);
assertThatJson(body).doesNotHaveJsonPath("$.store.music");
```

`jsonpath-spring-test` replaces Spring's Jayway-based `jsonPath(...)` matchers for MockMvc and
`WebTestClient`. It uses the Spring version of your project; it is tested with Spring Framework 7.0 and
also passed its tests once with 6.2.19, whose line no longer gets public security fixes.

```java
import static io.github.treetrail.jsonpath.spring.TreetrailResultMatchers.jsonPath;

mockMvc.perform(get("/store"))
        .andExpect(jsonPath("$.store.bicycle.color").value("red"))
        .andExpect(jsonPath("$.store.book[?@.price < 10].title").values("Sayings", "Moby Dick"))
        .andExpect(jsonPath("$.store.music").doesNotExist());

// WebTestClient: import static io.github.treetrail.jsonpath.spring.TreetrailWebTestClient.jsonPath;
webTestClient.get().uri("/store").exchange()
        .expectBody().consumeWith(jsonPath("$.store.bicycle.color").value("red"));
```

A query always selects a list of nodes, so `value(x)` means exactly one node with value `x` and
`values(...)` exactly these values in this order. Failures name the expression and show the values found
with their paths.

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

### Jayway idioms and their equivalents

| Jayway JsonPath | Treetrail |
| --- | --- |
| `MockMvcResultMatchers.jsonPath("$.a").value(1)` | `TreetrailResultMatchers.jsonPath("$.a").value(1)` from `jsonpath-spring-test` |
| `JsonPath.read(document, "$.a.b")` | `JsonPath.compile("$.a.b").query(document).single()` returns an `Optional<Node>`; `.map(Node::value)` gives the value |
| `JsonPath.parse(json).read("$.a", Integer.class)` | No type mapping: cast the value of plain Java objects, or use your library's mapping, e.g. `objectMapper.treeToValue(node, Integer.class)` with `Jackson2Model` |
| `Option.ALWAYS_RETURN_LIST` | Always the case |
| `Option.DEFAULT_PATH_LEAF_TO_NULL` | `single()` is empty for a missing member and holds a node with a `null` value for JSON `null`; `.map(Node::value).orElse(null)` returns `null` in both cases |
| `Option.SUPPRESS_EXCEPTIONS` | Not needed: missing paths select nothing instead of throwing; only invalid expressions throw, when they are compiled |
| `$.items.length()` | `JsonPath.compile("$.items[*]").query(document).size()`, or `length(@.items)` inside a filter |
| `.min()`, `.max()`, `.sum()`, `.avg()` | Select the values and aggregate them in Java, e.g. with `values().stream()` |
| `[?(@.name =~ /^a.*/i)]` | `[?match(@.name, '[aA].*')]` for a full match, `search()` for a substring; I-Regexp has no flags and no `\d`, `\w`, `\s` |
| `[?(@.size in ['S', 'M'])]` | `[?@.size == 'S' \|\| @.size == 'M']` |
| `JsonPath.parse(json).set("$.store.book[*].price", 0)` | Queries are read-only, but adapters return your library's own nodes: `query(document, Jackson2Model.INSTANCE)` and then `((ObjectNode) node.value()).put("price", 0)` on each selected book |
| `JsonPath.parse(json).delete("$.store.book[*].isbn")` | Select the parents (`$.store.book[*]`) and remove the member with your library, e.g. `((ObjectNode) node.value()).remove("isbn")` |

### Checking your expressions before you switch

The `jsonpath-migration` module runs your expressions with Jayway JsonPath and with this library
and reports every difference, with hints for rewriting Jayway-only syntax. Add it as a test
dependency; it uses the Jayway JsonPath version your project already depends on (tested with 2.10.0 and
3.0.0) and adds none of its own. Pass your application's Jayway `Configuration` so that options like
`DEFAULT_PATH_LEAF_TO_NULL` are taken into account.

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
Compliance Test Suite case by case (`./gradlew jaywayReport` regenerates it). Jayway 2.10.0 gives exactly
the same results (`./gradlew jaywayReport2`).

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

## Conformance

[docs/conformance.md](docs/conformance.md) describes the details: the one deviation from the RFC
(`^` and `$` act as anchors in regular expressions, as the Compliance Test Suite expects), how numbers
and strings are compared, all limits, and what happens with documents that are not I-JSON, such as
`NaN`, cycles in Java objects or Java types other than `Map` and `List`.

## Testing

Besides the Compliance Test Suite, which runs against every JSON model on Java 17, 21 and 25:

- **Fuzzing** with [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer): compiling expressions,
  querying documents, matching regular expressions and parsing JSON may only throw the documented
  exceptions and must finish each input within five seconds. Every CI build fuzzes each target for
  15 seconds, a nightly workflow for 10 minutes; `./gradlew :jsonpath-core:fuzz -PfuzzDuration=10m`
  runs it locally.
- **Property tests** with [jqwik](https://jqwik.net): every normalized path selects exactly its node,
  `$..*` visits each node once, compiling is deterministic, and comparisons are consistent across
  Java number types.
- **Differential tests:** 20,000 random regular expressions against `java.util.regex`, and 2,000 random
  queries against Python's [jsonpath-rfc9535](https://github.com/jg-rp/python-jsonpath-rfc9535). The
  latter found five bugs in the reference, and analysing them a sixth, listed in
  [scripts/differential/README.md](scripts/differential/README.md) and reported upstream.
- **Concurrency:** 16 threads share compiled queries while the regex automata are being built.
- **Mutation testing** with [PIT](https://pitest.org) every night: of 1,399 small changes to the core's
  bytecode, the tests detect 87 %; the run fails below 85 %. `./gradlew :jsonpath-core:pitest` runs it
  locally, the report is in `jsonpath-core/build/reports/pitest`.

## Building

```bash
./gradlew build
```

The build compiles with JDK 25 for Java 17 and runs the unit tests and the compliance suite on
Java 17, 21 and 25. Gradle downloads the JDKs it does not find.

The build also fails if a published module's API becomes incompatible with the last release (see
[docs/releasing.md](docs/releasing.md#api-compatibility)) or if a module's line or branch coverage falls below
its minimum in `build.gradle.kts`. Coverage reports are in `build/reports/jacoco/test/html` of each module.
Java sources are formatted with [palantir-java-format](https://github.com/palantir/palantir-java-format):
`./gradlew spotlessApply` formats them, and the build fails on unformatted code.

## License

[Apache License 2.0](LICENSE). The vendored Compliance Test Suite (test sources only) is licensed
under BSD-2, see [NOTICE](NOTICE).
