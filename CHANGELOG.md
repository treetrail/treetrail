# Changelog

All notable changes to Treetrail. Versions follow [Semantic Versioning](https://semver.org); before 1.0
a minor version may still change the API, which this file then says.

## 0.2.0 – unreleased

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
  differential test found six bugs in the reference implementation, reported as
  jg-rp/python-jsonpath-rfc9535#24 to #27. ([#16](https://github.com/treetrail/treetrail/issues/16),
  [#43](https://github.com/treetrail/treetrail/pull/43))

## 0.1.0 – 2026-10-06

First release on Maven Central: `jsonpath-core` (RFC 9535, all 706 cases of the JSONPath Compliance Test
Suite, no dependencies, linear-time I-Regexp), adapters for Jackson 2, Jackson 3, Gson and Jakarta JSON-P,
`jsonpath-migration` (comparison with Jayway JsonPath) and `jsonpath-rewrite` (OpenRewrite recipe).
