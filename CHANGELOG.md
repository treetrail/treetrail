# Changelog

All notable changes to Treetrail. Versions follow [Semantic Versioning](https://semver.org); before 1.0
a minor version may still change the API, which this file then says.

## Unreleased

### Added

- Nullness annotations: every package is `@NullMarked` ([JSpecify](https://jspecify.dev)), and values that can
  be `null` are `@Nullable`, for example JSON `null` in `JavaObjectModel` (now a `JsonModel<@Nullable Object>`,
  so `query(Object)` returns a `NodeList<@Nullable Object>`), `JsonModel.findMember` and
  `JsonModel.numberValue`. Kotlin and other null-aware tools see these types. The annotations come from
  `org.jspecify:jspecify` (one small jar without dependencies): Gradle puts it on the compile class path
  only, Maven also on the runtime class path; the module descriptors say `requires static transitive`.
  No change in behavior.
  ([#19](https://github.com/treetrail/treetrail/issues/19))
- `NormalizedPath`: a node's location as a value with typed steps (`Name`, `Index`), parsed from and printed
  as a normalized path, and converted to a JSON Pointer (RFC 6901) with `toJsonPointer()`.
  `Node.normalizedPath()` returns it. `JsonPath` has value semantics (`equals`/`hashCode` by expression and
  limits) and an `expression()` accessor. `Node.hashCode()` now hashes the path only, instead of hashing
  the value deeply and rebuilding the path string on every call.
  ([#14](https://github.com/treetrail/treetrail/issues/14))
- `JsonModel.jsonEquals(modelA, a, modelB, b)`: JSON value equality with the semantics of `==` in filters,
  also across models, for example a Jackson tree against plain Java objects. The migration module and the
  compliance tests now use it instead of their own implementations.
  ([#21](https://github.com/treetrail/treetrail/issues/21))
- Function extensions (RFC 9535, section 2.4): `FunctionExtension.value(...)`, `logical(...)` and `nodes(...)`
  declare a function with typed parameters; `JsonPath.compiler().withFunctions(...)` compiles queries that use
  it, type-checked like the built-in functions, which are written against the same types. Functions see
  arguments as `FunctionValue` (independent of the JSON model) and cannot create arrays or objects.
  ([#13](https://github.com/treetrail/treetrail/issues/13))
- `JsonPath.exists(document)` and `JsonPath.first(document)` (also with a model): stop at the first node
  instead of selecting all of them. ([#12](https://github.com/treetrail/treetrail/issues/12))
- `jsonpath-model-testkit`: tests for your own `JsonModel` implementation. `JsonModelTestKit.complianceTests`
  runs all 706 cases of the JSONPath Compliance Test Suite through it, `contractTests` checks each method of
  `JsonModel`. JUnit dynamic tests, no Jackson dependency; the suite (BSD-2) is included.
  ([#15](https://github.com/treetrail/treetrail/issues/15))

### Changed

- `FindJaywayJsonPathExpressions` (jsonpath-rewrite) also finds expressions in compile-time constants
  (`static final String`, also of other classes and built with `+`), assesses Spring's format templates such as
  `jsonPath("$.items[%d].name", 0)` with their arguments, covers `WebTestClient`'s `jsonPath(...)` and Kotlin
  sources. It is now a `ScanningRecipe`, so `getVisitor()` is final; the recipe class was already final.
  ([#18](https://github.com/treetrail/treetrail/issues/18))
- Faster queries: nodes pass through the segments one by one instead of in a list per segment, existence
  tests in filters (`[?@..x]`) and comparisons with singular queries stop at the first node, descendant
  segments compute each node's children once, literals are converted once per query, and the pattern of
  `match()`/`search()` is compiled once per query when it is a literal. On the benchmark documents:
  filter 8 %, descendant 14 %, regex 21 % (32 % on Jackson trees) and definite paths 31 % faster.
  ([#12](https://github.com/treetrail/treetrail/issues/12))
- A run that exceeds `maxResultSize` stops as soon as it selects one node too many; the message now says
  "Query selected more than N nodes, the limit".

### Testing

- The differential test runs against jsonpath-rfc9535 2.0.1, which fixes all six bugs it had found in
  the reference ([#24](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/24)–[#27](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/27)).
  The list of known differences is now empty. The query generator no longer avoids those bugs: it writes
  Unicode escapes for control characters and other characters with hex digits in either case, shorthand
  names above U+FFFF, trailing commas, `-` after shorthand names and ordering comparisons with booleans.
  Against 2.0.0, the regenerated corpus differs on 299 of its 2000 queries; against 2.0.1, on none.

## 0.2.0 – 2026-10-08

The first announced release. No breaking changes: the public API of 0.1.0 is unchanged, everything
below is an addition or a fix.

### Added

- `jsonpath-assertj`: AssertJ assertions on JSON text, comparing values as JSON values
  (`399`, `399L` and `399.0` all match `399`). ([#40](https://github.com/treetrail/treetrail/pull/40))
- `jsonpath-spring-test`: MockMvc and WebTestClient matchers that replace Spring's Jayway-based
  `jsonPath(...)`. Tested with Spring Framework 7; a one-time run with 6.2.19 passed as well.
  ([#40](https://github.com/treetrail/treetrail/pull/40))
- A strict JSON parser in the core, so JSON text can be queried without a JSON library:
  `JsonPath.queryJson(String)`, `JavaObjectModel.parse(String)`, `JavaObjectModel.jsonEquals(Object, Object)`
  and `InvalidJsonException`. ([#40](https://github.com/treetrail/treetrail/pull/40))

### Fixed

- Regular expressions taken from documents, as in `match(@.value, @.pattern)`, could make the regex
  caches retain hundreds of megabytes: 255 distinct patterns kept 411 MB, 781 MB with `search()` as well.
  The caches now hold about 10 MB at most. ([#41](https://github.com/treetrail/treetrail/issues/41),
  [#42](https://github.com/treetrail/treetrail/pull/42))
- Compiling a regular expression that repeats an empty group a huge number of times, such as
  `(){1390468697}`, took tens of seconds. Found by the fuzzer.
  ([#43](https://github.com/treetrail/treetrail/pull/43))
- `JsonPath.compile(null)` and `JavaObjectModel.parse(null)` throw a `NullPointerException` that names the
  parameter instead of failing inside the parser. ([#43](https://github.com/treetrail/treetrail/pull/43))

### Testing

- Fuzzing with Jazzer in every CI build and every night, property tests with jqwik, differential tests
  against `java.util.regex` and against Python's jsonpath-rfc9535, and a concurrency test. The
  differential test found five bugs in the reference implementation, and analysing them a sixth; all six
  are reported upstream ([#24](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/24),
  [#25](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/25),
  [#26](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/26),
  [#27](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/27)).
  ([#16](https://github.com/treetrail/treetrail/issues/16), [#43](https://github.com/treetrail/treetrail/pull/43))

## 0.1.0 – 2026-10-06

First release on Maven Central: `jsonpath-core` (RFC 9535, all 706 cases of the JSONPath Compliance Test
Suite, no dependencies, linear-time I-Regexp), adapters for Jackson 2, Jackson 3, Gson and Jakarta JSON-P,
`jsonpath-migration` (comparison with Jayway JsonPath) and `jsonpath-rewrite` (OpenRewrite recipe).
