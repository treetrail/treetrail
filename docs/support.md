# Versions and support

## Versioning

Treetrail follows [Semantic Versioning](https://semver.org). Before 1.0, a minor version may change the
API; [CHANGELOG.md](../CHANGELOG.md) then says so. From 1.0 on, incompatible changes need a new major
version.

The API of a module is its exported packages. `io.github.treetrail.jsonpath.internal` is not exported and
can change in any release. Every build compares the API of the published modules with the last release
([docs/releasing.md](releasing.md#api-compatibility)).

A change in which nodes a query selects counts as a fix if the old result contradicted RFC 9535, and is
listed under **Fixed** in the changelog.

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
