# Benchmarks

This library compared with Jayway JsonPath 3.0.0 (default configuration), measured with JMH.
Lower is better; "Jayway / this" above 1 means this library is faster.

Environment: Apple M5 Pro (18 cores), 48 GB RAM, macOS, Temurin 25.0.3+9-LTS, JMH 1.37,
1 fork, 3 × 1 s warmup, 5 × 1 s measurement (hostile regex: 2 × 1 s / 3 × 1 s), average time per operation.
Raw results: [jmh-results-2026-10-05.json](benchmarks/jmh-results-2026-10-05.json). Reproduce with
`./gradlew :jsonpath-benchmarks:jmh`.

## Queries

Bookstore document with 1,000 books as plain Java objects. Both libraries get the same document and
a precompiled expression, and the benchmark setup checks that both select the same values.
This library's time includes building the node list with locations.

| Query | Expression | This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- | --- | --- |
| definite | `$.store.bicycle.color` | 0.081 ± 0.001 | 0.079 ± 0.002 | 0.98× |
| wildcard | `$.store.book[*].title` | 27.3 ± 0.6 | 48.5 ± 1.5 | 1.77× |
| filter | `$.store.book[?(@.price < 10 && @.category == 'fiction')].title` | 97.0 ± 0.3 | 120.2 ± 0.7 | 1.24× |
| descendant | `$..price` | 148.1 ± 0.6 | 146.2 ± 2.6 | 0.99× |
| regex | `match(@.author, 'H.*')` / `=~ /H.*/` | 58.3 ± 1.2 | 71.2 ± 1.1 | 1.22× |

## Queries on Jackson trees

The same queries on the same document, read into a Jackson 2 `JsonNode` tree, the most common setup
in practice. This library queries it through `Jackson2Model`, Jayway JsonPath through its
`JacksonJsonNodeJsonProvider` and `JacksonMappingProvider`; neither converts the tree. The setup again
checks that both select the same nodes. Measured on 2026-10-09 with the code of 0.2.0, same machine and
settings; raw results: [jmh-results-jackson-2026-10-09.json](benchmarks/jmh-results-jackson-2026-10-09.json).
`./gradlew :jsonpath-benchmarks:jmh -PjmhArgs="JacksonQueryBenchmark"` reproduces them.

| Query | Expression | This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- | --- | --- |
| definite | `$.store.bicycle.color` | 0.091 ± 0.004 | 0.121 ± 0.0004 | 1.33× |
| wildcard | `$.store.book[*].title` | 29.8 ± 1.4 | 66.2 ± 0.3 | 2.22× |
| filter | `$.store.book[?(@.price < 10 && @.category == 'fiction')].title` | 92.3 ± 0.4 | 154.4 ± 1.3 | 1.67× |
| descendant | `$..price` | 168.1 ± 0.4 | 257.5 ± 1.2 | 1.53× |
| regex | `match(@.author, 'H.*')` / `=~ /H.*/` | 86.4 ± 2.8 | 106.1 ± 1.2 | 1.23× |

## Compiling an expression

`$.store.book[?(@.price < 10 && @.category == 'fiction')].title`, for code that does not cache compiled paths.

| This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- |
| 0.270 ± 0.009 | 0.460 ± 0.038 | 1.70× |

## Hostile regular expression

`((a+)+)+b` matched against n × `a` followed by `!`, through `match()` here and `=~` in Jayway
(which uses `java.util.regex`). A backtracking engine needs time exponential in n; this library's
automaton needs linear time. Since JDK 9, `java.util.regex` defuses many textbook cases such as
`(a|a)*b` or `(a+)+b`, so those are no longer a fair demonstration; triply nested quantifiers are
still exponential on JDK 25.

| n | This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- | --- |
| 10 | 0.064 ± 0.002 | 27.7 ± 0.1 | 434× |
| 15 | 0.068 ± 0.002 | 798.9 ± 3.8 | 11 732× |
| 20 | 0.070 ± 0.003 | 24 659 ± 102 | 350 215× |
| 24 | 0.073 ± 0.002 | 412 961 ± 3 609 | 5 641 374× |

## Caveats

- One machine, one JDK, one run. Treat differences under about 10 % as noise.
- Jayway reads the plain-Java document through its default json-smart provider, and the Jackson tree
  through its Jackson providers; with other providers (Gson) its numbers differ.
- The regex query benefits from this library's cached automaton (a lazy DFA, see `IRegexp`).
