# Releasing

Releases go to Maven Central under `io.github.treetrail` through
[.github/workflows/release.yml](../.github/workflows/release.yml). Pushing a tag `vX.Y.Z` builds and tests
everything, attests the files, waits for approval and publishes all eleven library modules.

## What a release contains

For each of `jsonpath-core`, `jsonpath-jackson2`, `jsonpath-jackson3`, `jsonpath-gson`, `jsonpath-jsonp`,
`jsonpath-assertj`, `jsonpath-spring-test`, `jsonpath-migration`, `jsonpath-rewrite`, `jsonpath-model-testkit`
and `jsonpath-jayway-functions`:

- the jar, the sources jar and the javadoc jar, each signed with GPG
- the POM and the Gradle module metadata
- a CycloneDX SBOM of the runtime dependencies (classifier `cyclonedx`, extension `json`)

The jars, POMs, module files and SBOMs are reproducible: two builds of the same commit produce identical
bytes. The SBOM's `metadata.timestamp` is the commit time of `HEAD` (or `SOURCE_DATE_EPOCH`, if set), not
the build time. Before publishing, the workflow attests the build provenance of all files (SLSA, GitHub artifact
attestations) and afterwards checks that the published files are the attested ones. The GitHub release
carries the same files. To verify a downloaded file:

```bash
gh attestation verify jsonpath-core-0.1.0.jar --repo treetrail/treetrail
```

## One-time setup

1. In the [Central Portal](https://central.sonatype.com), add and verify the namespace
   `io.github.treetrail`; the portal names a public repository to create in the `treetrail` GitHub
   organization for that.
2. Create the GitHub environment `maven-central` in this repository with you as required reviewer and a
   deployment rule for tags `v*`.
3. Add these environment secrets: `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Central
   Portal user token), `SIGNING_KEY` (the ASCII-armored private key), `SIGNING_KEY_ID` (the last eight
   characters of the key ID) and `SIGNING_KEY_PASSWORD`.

## Making a release

1. Make sure `main` is green and the README describes the release.
2. Optionally run the workflow manually (Actions → Release → Run workflow): a dry run that builds and
   uploads the files as a workflow artifact, without publishing.
3. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`.
4. Approve the `maven-central` deployment when the workflow asks.
5. Once the release is on Maven Central, set `treetrail.apiBaselineVersion` in `gradle.properties` to the new
   version and clear `treetrail.unreleasedModules` there and `acceptedApiChanges` in the modules' build files
   (`grep -r acceptedApiChanges */build.gradle.kts`), so that the API of every module is compared with the new
   release from then on.

## API compatibility

`./gradlew build` runs `apiCompatibility` in every published module: [japicmp](https://github.com/siom79/japicmp)
compares the module's public API with the release named by `treetrail.apiBaselineVersion` in `gradle.properties`
and fails on binary or source incompatible changes. The `internal` package is not exported and not compared.
Modules that are not in that release yet are listed in `treetrail.unreleasedModules` and skipped. A report is
written to `build/reports/api-compatibility/<module>.html`. The check is part of the convention plugin
`treetrail.published-conventions` in `build-logic`.

Before 1.0, a minor release may still break the API. An intended incompatible change goes into
`treetrail { acceptedApiChanges.add(...) }` in the module's `build.gradle.kts`, in japicmp's exclude syntax
(`io.github.treetrail.jsonpath.JsonPath#compile(java.lang.String)`), together with a note in `CHANGELOG.md`.

Maven Central releases cannot be changed or deleted. A broken release is fixed with a new version.
