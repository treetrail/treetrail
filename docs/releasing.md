# Releasing

Releases go to Maven Central under `io.github.treetrail` through
[.github/workflows/release.yml](../.github/workflows/release.yml). Pushing a tag `vX.Y.Z` builds and tests
everything, attests the files, waits for approval and publishes all nine library modules.

## What a release contains

For each of `jsonpath-core`, `jsonpath-jackson2`, `jsonpath-jackson3`, `jsonpath-gson`, `jsonpath-jsonp`,
`jsonpath-assertj`, `jsonpath-spring-test`, `jsonpath-migration` and `jsonpath-rewrite`:

- the jar, the sources jar and the javadoc jar, each signed with GPG
- the POM and the Gradle module metadata
- a CycloneDX SBOM of the runtime dependencies (classifier `cyclonedx`, extension `json`)

The jars, POMs and module files are reproducible: two builds of the same commit produce identical
bytes. Before publishing, the workflow attests the build provenance of all files (SLSA, GitHub artifact
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

Maven Central releases cannot be changed or deleted. A broken release is fixed with a new version.
