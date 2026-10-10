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
- `jsonpath-model-testkit`: tests for your own `JsonModel` implementation. `JsonModelTestKit.complianceTests`
  runs all 706 cases of the JSONPath Compliance Test Suite through it, `contractTests` checks each method of
  `JsonModel`. JUnit dynamic tests, no Jackson dependency; the suite (BSD-2) is included.
  ([#15](https://github.com/treetrail/treetrail/issues/15))

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
