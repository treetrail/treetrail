#!/usr/bin/env bash
# Updates the vendored JSONPath Compliance Test Suite to the latest commit of its main branch, or to the
# commit given as the first argument, and the places that name its commit, date and number of cases.
#
#   scripts/update-cts.sh [commit]
#
# Prints "up to date" and changes nothing if the suite is already at that commit. Otherwise it writes
# the new values to $GITHUB_OUTPUT when set (old_commit, new_commit, old_cases, new_cases), so that
# .github/workflows/cts-update.yml can describe the update. The changelog is left to the maintainer.
set -euo pipefail

repo=jsonpath-standard/jsonpath-compliance-test-suite
dir=jsonpath-model-testkit/src/main/resources/io/github/treetrail/jsonpath/testkit/cts
testkit_test=jsonpath-model-testkit/src/test/java/io/github/treetrail/jsonpath/testkit/JsonModelTestKitTest.java

cd "$(dirname "$0")/.."

api() {
  curl -fsSL -H "Accept: application/vnd.github+json" \
    ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} "https://api.github.com/repos/$repo/$1"
}

old_commit=
old_date=
if [[ "$(<"$dir/README.md")" =~ at\ commit\ .([0-9a-f]{40}).\ \(([0-9-]{10})\) ]]; then
  old_commit=${BASH_REMATCH[1]}
  old_date=${BASH_REMATCH[2]}
fi
old_cases=$(jq '.tests | length' "$dir/cts.json")
if [[ -z "$old_commit" || -z "$old_date" ]]; then
  echo "Cannot read the current commit and date from $dir/README.md" >&2
  exit 1
fi

commit_json=$(api "commits/${1:-main}")
new_commit=$(jq -r '.sha' <<<"$commit_json")
new_date=$(jq -r '.commit.committer.date[0:10]' <<<"$commit_json")
if [[ ! "$new_commit" =~ ^[0-9a-f]{40}$ || ! "$new_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "Unexpected commit or date from the GitHub API: $new_commit $new_date" >&2
  exit 1
fi
if [[ "$new_commit" == "$old_commit" ]]; then
  echo "up to date: $old_commit"
  exit 0
fi

curl -fsSL "https://raw.githubusercontent.com/$repo/$new_commit/cts.json" -o "$dir/cts.json.new"
new_cases=$(jq '.tests | length' "$dir/cts.json.new")
mv "$dir/cts.json.new" "$dir/cts.json"

old_short=${old_commit:0:7}
new_short=${new_commit:0:7}
sed -i.bak "s/$old_commit/$new_commit/; s/($old_date)/($new_date)/" "$dir/README.md"
sed -i.bak "s/hasSize($old_cases)/hasSize($new_cases)/" "$testkit_test"
for doc in README.md docs/conformance.md; do
  sed -i.bak -e "s/\`$old_short\`/\`$new_short\`/g" -e "s/$old_date/$new_date/g" \
    -e "s/all $old_cases cases/all $new_cases cases/g" -e "s/$old_cases cases,/$new_cases cases,/g" "$doc"
done
rm -f "$dir/README.md.bak" "$testkit_test.bak" README.md.bak docs/conformance.md.bak

echo "updated: $old_short ($old_date, $old_cases cases) -> $new_short ($new_date, $new_cases cases)"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "old_commit=$old_commit"
    echo "new_commit=$new_commit"
    echo "old_cases=$old_cases"
    echo "new_cases=$new_cases"
  } >>"$GITHUB_OUTPUT"
fi
