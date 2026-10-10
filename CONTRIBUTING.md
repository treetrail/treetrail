# Contributing to Treetrail

Thanks for helping. Bug reports, failing expressions, documentation fixes and pull requests are all
welcome. For security problems, please follow [SECURITY.md](SECURITY.md) instead of opening an issue.

## Reporting a bug

A useful report names the module and version, the JSON model (plain Java objects, Jackson, Gson, JSON-P)
and gives the expression, the document, the result you expected and the one you got. If the expression
behaves differently in another JSONPath implementation, say which one; for Jayway JsonPath,
[docs/jayway-vs-rfc9535.md](docs/jayway-vs-rfc9535.md) lists the differences that are intended.

RFC 9535 is the reference: a result that differs from the RFC or from the
[Compliance Test Suite](https://github.com/jsonpath-standard/jsonpath-compliance-test-suite) is a bug,
except for the deviations in [docs/conformance.md](docs/conformance.md).

## Building and testing

You need a JDK to start Gradle; the build uses a JDK 25 toolchain and runs the tests on Java 17, 21 and 25,
downloading the JDKs it does not find.

```bash
./gradlew build
```

`build` compiles, runs all tests including the Compliance Test Suite on every JSON model, and checks:

- **Formatting** with palantir-java-format. `./gradlew spotlessApply` formats the sources.
- **Error Prone and NullAway.** Every package is `@NullMarked`; mark values that can be `null` with
  `@Nullable`.
- **API compatibility** of the published modules against the last release. Before 1.0 an incompatible
  change is possible but deliberate: list it in `treetrail { acceptedApiChanges.add(...) }` in the module's
  `build.gradle.kts` and explain it in the changelog. See [docs/releasing.md](docs/releasing.md#api-compatibility).
- **Coverage:** each module sets a minimum line and branch coverage with `treetrail { coverage(...) }` in its
  `build.gradle.kts`. The shared build configuration is in `build-logic`, as convention plugins.

Mutation testing (`./gradlew :jsonpath-core:pitest`) and long fuzzing runs
(`./gradlew :jsonpath-core:fuzz -PfuzzDuration=10m`) run every night; run them locally when you change the
evaluator, the parser or the regular expression engine.

## Pull requests

- One topic per pull request, with tests. A change in behavior needs a test that fails without it.
- Add an entry under **Unreleased** in [CHANGELOG.md](CHANGELOG.md), with a link to the issue.
- Performance claims need numbers: the JMH benchmarks are in `jsonpath-benchmarks`
  (`./gradlew :jsonpath-benchmarks:jmh -PjmhArgs="Query.*"`).
- Write documentation and comments in plain English and match the style of the surrounding code.
- CI must pass. Pull requests are squash-merged.

By contributing, you agree that your contribution is licensed under the [Apache License 2.0](LICENSE),
like the rest of the project.

## Versions and support

See [docs/support.md](docs/support.md) for the versioning policy and the tested Java and library versions.
