#!/usr/bin/env bash
# Copies the files a release publishes (jars, POMs, Gradle module files, CycloneDX SBOMs) of every
# published module into one directory, named as on Maven Central. Used by .github/workflows/release.yml
# to attest, verify and attach exactly these files.
#
#   scripts/collect-release-files.sh <version> <target directory>
set -euo pipefail

version="$1"
target="$2"
modules=(jsonpath-core jsonpath-jackson2 jsonpath-jackson3 jsonpath-gson jsonpath-jsonp jsonpath-migration jsonpath-rewrite)

mkdir -p "$target"
for module in "${modules[@]}"; do
  base="$module-$version"
  cp "$module/build/libs/$base.jar" "$module/build/libs/$base-sources.jar" "$module/build/libs/$base-javadoc.jar" "$target/"
  cp "$module/build/publications/maven/pom-default.xml" "$target/$base.pom"
  cp "$module/build/publications/maven/module.json" "$target/$base.module"
  cp "$module/build/sbom/$module-cyclonedx.json" "$target/$base-cyclonedx.json"
done
echo "Collected $(find "$target" -type f | wc -l | tr -d ' ') files into $target"
