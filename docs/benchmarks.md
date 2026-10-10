# Benchmarks

This library compared with Jayway JsonPath 3.0.0 (default configuration), measured with JMH.
Lower is better; "Jayway / this" above 1 means this library is faster.

Environment: Apple M5 Pro (18 cores), 48 GB RAM, macOS, Temurin 25.0.3+9-LTS, JMH 1.37,
10 forks, 3 × 1 s warmup, 5 × 1 s measurement per fork (hostile regex: 2 × 1 s / 3 × 1 s), average time
per operation, mean over all forks with JMH's 99.9 % confidence interval. The tables up to the hostile
regular expression come from one run on 2026-10-10 (41 minutes); raw results:
[jmh-results-2026-10-10.json](benchmarks/jmh-results-2026-10-10.json). The large document and the other
implementations come from a second run that day with the same settings
([jmh-results-2026-10-10-large-and-others.json](benchmarks/jmh-results-2026-10-10-large-and-others.json)),
the allocations from a third with `-prof gc` and 2 forks
([jmh-allocation-2026-10-10.json](benchmarks/jmh-allocation-2026-10-10.json)).
Reproduce with `./gradlew :jsonpath-benchmarks:jmh`.

Each benchmark's JVM only runs the library it measures; `./gradlew :jsonpath-benchmarks:test` checks
that both libraries select the same values for every query (see [Method](#method)).

## Queries

Bookstore document with 1,000 books as plain Java objects. Both libraries get the same document and
a precompiled expression. This library's time includes building the node list with locations.

| Query | Expression | This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- | --- | --- |
| definite | `$.store.bicycle.color` | 0.055 ± 0.0004 | 0.078 ± 0.0005 | 1.41× |
| wildcard | `$.store.book[*].title` | 26.2 ± 0.2 | 59.1 ± 4.7 ¹ | 2.26× |
| filter | `$.store.book[?(@.price < 10 && @.category == 'fiction')].title` | 82.5 ± 0.5 | 116.8 ± 0.5 | 1.42× |
| descendant | `$..price` | 133.0 ± 1.5 | 151.3 ± 2.5 | 1.14× |
| regex | `match(@.author, 'H.*')` / `=~ /H.*/` | 41.1 ± 0.8 | 70.5 ± 0.3 | 1.72× |

¹ Jayway's wildcard query runs at one of two speeds per JVM: 5 of the 10 forks took 48.8 to 50.5 µs,
the other 5 took 67.1 to 69.2 µs. Which one a JVM gets depends on the order in which the JIT compiler
compiles Jayway's methods, not on the input. Against the faster group alone the ratio is 1.9×, against
the slower one 2.6×.

## Queries on Jackson trees

The same queries on the same document, read into a Jackson 2 `JsonNode` tree, the most common setup
in practice. This library queries it through `Jackson2Model`, Jayway JsonPath through its
`JacksonJsonNodeJsonProvider` and `JacksonMappingProvider`; neither converts the tree.
`./gradlew :jsonpath-benchmarks:jmh -PjmhArgs="JacksonQueryBenchmark"` reproduces this table alone.

| Query | Expression | This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- | --- | --- |
| definite | `$.store.bicycle.color` | 0.060 ± 0.0004 | 0.182 ± 0.074 ² | 2.09× ² |
| wildcard | `$.store.book[*].title` | 31.1 ± 0.4 | 67.5 ± 2.2 | 2.17× |
| filter | `$.store.book[?(@.price < 10 && @.category == 'fiction')].title` | 85.8 ± 0.9 | 162.2 ± 1.6 | 1.89× |
| descendant | `$..price` | 142.8 ± 0.7 | 259.6 ± 1.3 | 1.82× |
| regex | `match(@.author, 'H.*')` / `=~ /H.*/` | 55.3 ± 0.2 | 104.6 ± 0.9 | 1.89× |

² 8 of Jayway's 10 forks took 0.121 to 0.128 µs, 2 took 0.235 and 0.588 µs, which raises the mean. The
ratio is therefore taken from the medians of the forks (0.127 / 0.060); from the means it would be 3.0×.

## Large document

The same queries on a bookstore with 92,000 books, 10.1 MB as JSON, as plain Java objects. Parsing is not
included. `./gradlew :jsonpath-benchmarks:jmh -PjmhArgs="LargeDocumentBenchmark"` reproduces this table.

| Query | This library (ms) | Jayway (ms) | Jayway / this |
| --- | --- | --- | --- |
| definite | 0.000056 ± 0.0000003 | 0.000080 ± 0.0000007 | 1.44× |
| wildcard | 2.72 ± 0.04 | 4.76 ± 0.05 | 1.75× |
| filter | 8.04 ± 0.11 | 12.17 ± 0.12 | 1.51× |
| descendant | 12.42 ± 0.09 | 13.79 ± 0.20 | 1.11× |
| regex | 4.25 ± 0.16 | 7.51 ± 0.15 | 1.77× |

This library's times grow linearly with the document: 92 times as many books take 93 to 104 times as long as
the 1,000-book queries in the same run (definite paths do not depend on the size).

## Other RFC 9535 implementations for Java

The queries of the first table with two other Java libraries that implement RFC 9535, each on its own
representation of the same bookstore (1,000 books), compiled and parsed in the setup:
[SJF4J](https://github.com/sjf4j-projects/sjf4j) 1.3.3 on the same plain Java objects, and
[ajp](https://github.com/xmljacquard/ajp) 0.0.7, which implements RFC 9535 in XSLT on Saxon, on the document
as XPath maps and arrays (`fn:parse-json`). `BenchmarkFairnessTest` checks that they select the same values
as this library. This library's times are from the same run.

| Query | This library (µs) | SJF4J (µs) | ajp (µs) |
| --- | --- | --- | --- |
| definite | 0.056 ± 0.001 | 0.014 ± 0.001 | 9.6 ± 0.1 |
| wildcard | 26.1 ± 0.2 | 11.0 ± 0.1 | 3 062 ± 33 |
| filter | 82.7 ± 0.3 | 59.1 ± 0.7 | 9 573 ± 59 |
| descendant | 134.2 ± 2.4 | 48.7 ± 1.1 | 17 458 ± 202 |
| regex | 40.9 ± 0.4 | — ³ | 7 575 ± 52 |

SJF4J is faster on the four queries it answers. It returns the selected values only, while this library builds a node list
in which every node carries its location (its normalized path), as RFC 9535 defines the result; the
allocations below show the difference. ajp's times reflect its design on XSLT and XPath, not tuned for speed.

³ SJF4J 1.3.3 selects no nodes for this query: its `match()` matches nothing when the pattern contains a
quantifier, even `'.*'`, so there is no comparable time. `BenchmarkFairnessTest` notices when that changes.

## Allocation

Bytes allocated per query (JMH's `gc.alloc.rate.norm`), for the benchmarks above. Lower is better.

| Query | This library | Jayway | Jayway / this |
| --- | --- | --- | --- |
| Plain Java objects, 1,000 books | | | |
| definite | 528 B | 736 B | 1.4× |
| wildcard | 169 KB | 414 KB | 2.4× |
| filter | 389 KB | 883 KB | 2.3× |
| descendant | 649 KB | 1 086 KB | 1.7× |
| regex | 231 KB | 673 KB | 2.9× |
| Jackson tree, 1,000 books | | | |
| definite | 528 B | 1 144 B | 2.2× |
| wildcard | 169 KB | 470 KB | 2.8× |
| filter | 314 KB | 1 055 KB | 3.4× |
| descendant | 649 KB | 1 590 KB | 2.5× |
| regex | 231 KB | 792 KB | 3.4× |
| Plain Java objects, 92,000 books | | | |
| wildcard | 15.6 MB | 40.7 MB | 2.6× |
| filter | 35.9 MB | 81.4 MB | 2.3× |
| descendant | 59.6 MB | 104.1 MB | 1.7× |
| regex | 21.2 MB | 61.9 MB | 2.9× |

The other implementations on 1,000 books: SJF4J 48 B (definite), 15 KB (wildcard), 163 KB (filter),
151 KB (descendant); ajp 95 KB, 25.9 MB, 53.5 MB, 153.9 MB and 46.8 MB (regex). This library
allocates a location for every node it visits and a node object for every node it selects.

## Compiling an expression

`$.store.book[?(@.price < 10 && @.category == 'fiction')].title`, for code that does not cache compiled paths.

| This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- |
| 0.283 ± 0.004 | 0.480 ± 0.008 | 1.69× |

## Hostile regular expression

`((a+)+)+b` matched against n × `a` followed by `!`, through `match()` here and `=~` in Jayway
(which uses `java.util.regex`). A backtracking engine needs time exponential in n; this library's
automaton needs linear time. Since JDK 9, `java.util.regex` defuses many textbook cases such as
`(a|a)*b` or `(a+)+b`, so those are no longer a fair demonstration; triply nested quantifiers are
still exponential on JDK 25.

| n | This library (µs) | Jayway (µs) | Jayway / this |
| --- | --- | --- | --- |
| 10 | 0.054 ± 0.001 | 28.5 ± 0.8 | 526× |
| 15 | 0.056 ± 0.002 | 826.6 ± 32.6 | 14 666× |
| 20 | 0.060 ± 0.001 | 24 893 ± 261 | 412 145× |
| 24 | 0.064 ± 0.001 | 392 601 ± 8 673 | 6 149 779× |

## Method

Each library has its own JMH state, so a benchmark's JVM only loads and runs the library it measures.
Until 2026-10-10 the setup ran both libraries' queries once in every JVM, to check that they select the
same values, and each benchmark ran in a single fork. Both distorted Jayway's wildcard time:

- The JIT compiler puts Jayway's wildcard query into one of two states per JVM (about 49 or 69 µs, see
  ¹ above). With one fork, the published value depended on which state that one JVM happened to reach.
  The earlier 48.5 µs came from the faster state.
- Running this library's query in the same JVM changed the odds. On the code of 2026-10-09, with the check
  in the setup, 0 of 30 forks reached the faster state; without the check, 11 of 30 did. The
  setup-time check now runs as a unit test instead (`BenchmarkFairnessTest`).

Ten forks per benchmark make the mean cover both states. Earlier results, measured with one fork and
the check in the setup, are kept for reference:
[jmh-results-2026-10-05.json](benchmarks/jmh-results-2026-10-05.json) and
[jmh-results-jackson-2026-10-09.json](benchmarks/jmh-results-jackson-2026-10-09.json).

## Smoke run in CI

[benchmark-smoke.yml](../.github/workflows/benchmark-smoke.yml) runs `QueryBenchmark` briefly (2 forks) on pull
requests that change the core or the benchmarks, and every week. It fails if this library becomes more than
25 % slower than Jayway JsonPath on any query in that run, which catches large regressions on any machine. Its
numbers are not published: shared CI machines are too noisy for that.

## Caveats

- One machine, one JDK, one run of ten forks per benchmark. Treat differences under about 10 % as noise.
- Jayway reads the plain-Java document through its default json-smart provider, and the Jackson tree
  through its Jackson providers; with other providers (Gson) its numbers differ.
- The regex query benefits from this library's cached automaton (a lazy DFA, see `IRegexp`).
