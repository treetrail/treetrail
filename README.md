# jsonpath (working title)

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
- **Java 17+**, a named JPMS module (`com.christophsens.jsonpath`).

> **Status:** pre-release, not yet published to Maven Central. The API may still change.

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

```java
JsonNode document = objectMapper.readTree(json);
NodeList<JsonNode> books = JsonPath.compile("$.store.book[?@.price < 10]")
        .query(document, Jackson2Model.INSTANCE);
```

Each adapter passes the full Compliance Test Suite with documents parsed by its own library at
default settings. For other object models, implement `JsonModel<N>` (nine small methods) and call
`query(document, model)`. An adapter for Jakarta JSON-P is planned.

Compiled queries are immutable and thread-safe. Compile once, reuse often.
Invalid queries throw a `JsonPathSyntaxException` with the position of the problem.

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

A migration helper that runs your existing expressions against both libraries and reports every
difference is planned.

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

The build compiles with JDK 25 for Java 17 and runs the unit tests and the compliance suite.

## License

[Apache License 2.0](LICENSE). The vendored Compliance Test Suite (test sources only) is licensed
under BSD-2, see [NOTICE](NOTICE).
