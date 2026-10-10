# Conformance

Treetrail implements [RFC 9535](https://www.rfc-editor.org/rfc/rfc9535) (JSONPath) with the five
functions the RFC defines: `length()`, `count()`, `match()`, `search()` and `value()`. This page lists
where it deviates, what it decides where the RFC leaves room, its limits, and how it treats documents
that are not [I-JSON](https://www.rfc-editor.org/rfc/rfc7493).

## Compliance Test Suite

Every build runs the [JSONPath Compliance Test Suite](https://github.com/jsonpath-standard/jsonpath-compliance-test-suite)
at commit `9d1a415` (2026-09-17), 706 cases, on Java 17, 21 and 25. All cases pass with each object model:

| Object model | Documents parsed by |
| --- | --- |
| Plain Java objects (`JavaObjectModel`) | Jackson 2 with default settings (`Double`), Jackson 2 with `BigDecimal`, Gson untyped output |
| `Jackson2Model`, `Jackson3Model` | Jackson's `readTree` with default settings |
| `GsonModel` | Gson's `JsonParser` |
| `JsonpModel` | JSON-P with Eclipse Parsson |
| A model that implements only the abstract methods of `JsonModel` | Jackson 2 with `BigDecimal` |

## Deviations

| Topic | RFC | Treetrail |
| --- | --- | --- |
| `^` and `$` in `match()` and `search()` | I-Regexp ([RFC 9485](https://www.rfc-editor.org/rfc/rfc9485)) lists them as ordinary characters | Anchors, as the Compliance Test Suite expects |
| Function extensions | Section 2.4 allows more functions | The five functions of the RFC; more only where a `JsonPathCompiler` is given them as `FunctionExtension`s |

## Decisions where the RFC leaves room

- **Order of object members:** wildcards, descendant segments and filters visit members in the order
  the object model returns them: insertion order for Jackson, Gson, JSON-P and `LinkedHashMap`, no
  defined order for `HashMap`.
- **Duplicate member names:** whatever the JSON parser keeps; I-JSON forbids them.
- **Numbers** are compared by value: `1`, `1L`, `1.0` and `new BigDecimal("1.00")` are equal.
  A `double` counts with its shortest decimal form (`Double.toString`), so a document value `8.95`
  equals the literal `8.95` even though the binary value differs slightly.
- **Strings** are compared by Unicode code points, without normalization: `"é"` (U+00E9) and
  `"é"` are different. `length()` counts code points.
- **Unicode categories** in regular expressions (`\p{L}` and so on) come from the JDK
  (`Character.getType`): Unicode 13.0 on Java 17, 15.0 on Java 21, 16.0 on Java 25. Code points
  assigned in later Unicode versions can match differently depending on the Java version.
- **Invalid regular expressions** make `match()` and `search()` return false, as the RFC requires. That
  includes expressions beyond the limits below.
- **Results** are node lists and may contain the same node more than once (`$[0,0]`). Each node has a
  normalized path (section 2.7). A member whose value is JSON `null` is a node with a `null` value,
  unlike a missing member, which selects nothing.

## Limits

| Limit | Value | When exceeded |
| --- | --- | --- |
| Nesting of filters, parentheses and function calls | 64 | `JsonPathSyntaxException` |
| Integers in index and slice selectors | ±(2^53 − 1) | `JsonPathSyntaxException` |
| Exponent of number literals | what `BigDecimal` accepts (about ±2^31) | `JsonPathSyntaxException` |
| Size of a regular expression | 20,000 automaton instructions, e.g. `(a{1000}){1000}` exceeds it | Invalid: `match()` and `search()` return false |
| Nesting of groups in a regular expression | 100 | Invalid: `match()` and `search()` return false |
| Cached deterministic states per regular expression and mode | 2,000, and about 1 MiB of memory | Matching continues without caching further states: slower, still linear |
| Memory of all cached regex automata together | about 8 MiB | All cached automata are discarded and rebuilt on demand |
| Cached compiled regular expressions from documents, per `match()` or `search()` call of a query | 64 expressions or 40,000 instructions | That call's cache is cleared and refilled |
| Visited nodes per query run | 100,000,000 (configurable) | `JsonPathLimitExceededException` |
| Nesting depth of the document | 1,000 (configurable) | `JsonPathLimitExceededException` |
| Nodes in a result | unlimited (configurable) | `JsonPathLimitExceededException` |

The configurable limits are set with `JsonPath.withLimits(EvaluationLimits)`. A query checks the
interrupt status of its thread whenever the count of visited nodes passes a multiple of 4,096 and then
stops with a `JsonPathEvaluationException`; the interrupt status stays set.

## Documents that are not I-JSON

| Input | Behaviour |
| --- | --- |
| NaN and infinities, e.g. `1e400` parsed as a `double` | Not comparable: `<`, `<=`, `>`, `>=` and `==` are false, `!=` is true |
| Plain Java maps that contain themselves | The query stops at the depth limit |
| Documents nested deeper than the depth limit | The query stops at the depth limit; values are compared without recursion, so raising the limit cannot overflow the stack |
| Java arrays (also primitive arrays) | JSON arrays |
| `Character` | JSON strings |
| `Set` and other collections without index | `JsonPathEvaluationException`; copy them into a `List` |
| Maps with keys that are not strings | `JsonPathEvaluationException` |
| Other Java types, including Jackson, Gson or JSON-P trees passed as plain Java objects | `JsonPathEvaluationException`; for those trees the message names the adapter |
| Member names with unpaired surrogates | Selected normally; the normalized path contains them unescaped, because section 2.7 cannot express them |

Each `JsonPathEvaluationException` from the object model carries the normalized path of the node
being processed and the original exception as cause.

## Parsing JSON text

`JavaObjectModel.parse` and `JsonPath.queryJson` read JSON text ([RFC 8259](https://www.rfc-editor.org/rfc/rfc8259))
with a strict parser and additionally enforce the I-JSON rules queries rely on:

| Input | Behaviour |
| --- | --- |
| Duplicate member names in an object | `InvalidJsonException` |
| Unpaired surrogates, escaped (`"\ud800"`) or raw | `InvalidJsonException` |
| Nesting deeper than 1,000 levels | `InvalidJsonException` |
| Byte order mark, comments, trailing commas, `NaN`, single quotes | `InvalidJsonException` |
| Integers | `Integer`, `Long` or `BigInteger`, the smallest that fits |
| Numbers with fraction or exponent | `BigDecimal`, exact; exponents beyond what `BigDecimal` accepts are rejected |
| Objects | `LinkedHashMap` in document order |

Every `InvalidJsonException` carries the position of the problem.

