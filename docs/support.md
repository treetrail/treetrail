# Versions and support

## Versioning

Treetrail follows [Semantic Versioning](https://semver.org). From 1.0.0 on, the API is stable: code written
and compiled against a 1.x release keeps compiling and working with every later 1.x release. Minor versions
add features, patch versions fix bugs, and an incompatible change needs a new major version. Before 1.0, minor
versions could change the API; [CHANGELOG.md](../CHANGELOG.md) says where they did.

Every build compares the API of the published modules with the last release and fails on binary or source
incompatible changes ([docs/releasing.md](releasing.md#api-compatibility)).

### What counts as API

- The public and protected types and members in the API package of each published module: the package its
  module descriptor exports, for example `io.github.treetrail.jsonpath` in `jsonpath-core` and
  `io.github.treetrail.jsonpath.jackson2` in `jsonpath-jackson2`. `jsonpath-migration`, `jsonpath-rewrite` and
  `jsonpath-spring-test` have no module descriptor, only an automatic module name, and one package each.
- The Maven coordinates (`io.github.treetrail:jsonpath-*`) and the module names.
- Which nodes a query selects, as RFC 9535 defines it. A change counts as a fix if the old result contradicted
  RFC 9535, and is listed under **Fixed** in the changelog.
- `JsonModel` is meant to be implemented: methods added to it are default methods. `FunctionValue` and
  `FunctionArguments` are implemented only by Treetrail and may gain methods in a minor version.

Not API, and free to change in any release:

- `io.github.treetrail.jsonpath.internal`, which `jsonpath-core` does not export.
- The text of exception messages, of `toString()` and of migration hints. Code that needs details of an error
  can use the exception's type and accessors, such as `JsonPathSyntaxException.position()`.
- Speed and memory use, and the order of object members in models that have none, such as `HashMap`.
- The cases of the Compliance Test Suite that `jsonpath-model-testkit` runs: it ships the suite's current
  version, whose new cases can fail a model that passed before.
- The Unicode version behind `\p{...}` in regular expressions; a new one is noted in the changelog.

## Supported releases

Fixes, including security fixes, go into the latest release. There are no maintenance branches.

## Java

Java 17 or later. Every build runs the tests on Java 17, 21 and 25.

## Libraries

`jsonpath-jackson2`, `jsonpath-jackson3`, `jsonpath-gson`, `jsonpath-jsonp` and `jsonpath-assertj` depend on
their library at the tested version. If your project declares another version, your build tool picks one:
Gradle the highest, Maven the one declared nearest to your project. `jsonpath-spring-test`,
`jsonpath-migration` and `jsonpath-model-testkit` bring no Spring, Jayway JsonPath or JUnit of their own and
use your project's version. `jsonpath-jayway-functions` needs only the core.

| Module | Library | Tested with |
| --- | --- | --- |
| `jsonpath-jackson2` | Jackson Databind 2.x | 2.22 |
| `jsonpath-jackson3` | Jackson Databind 3.x | 3.2 |
| `jsonpath-gson` | Gson | 2.14 |
| `jsonpath-jsonp` | Jakarta JSON Processing API 2.1 | Parsson 1.1 as the implementation |
| `jsonpath-assertj` | AssertJ | 3.27 |
| `jsonpath-spring-test` | Spring Framework | 7.0; its tests also passed once with 6.2.19 |
| `jsonpath-migration` | Jayway JsonPath | 2.10 and 3.0 |
| `jsonpath-model-testkit` | JUnit Jupiter | 6.1 |
| `jsonpath-rewrite` | OpenRewrite | rewrite-bom 8.90 |

Other versions of the same major line are likely to work but are not tested. Dependabot proposes updates
of the tested versions every week.
