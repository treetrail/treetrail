#!/usr/bin/env bash
# Regenerates jsonpath-core/src/test/resources/differential/expected.json: random documents and queries
# (DifferentialCases), evaluated by the reference implementation pinned in requirements.txt.
#
#   scripts/differential/regenerate.sh [seed] [documents] [queries per document]
#
# Needs Python 3.12 or later (set PYTHON if python3 is older). The virtual environment lives in
# build/differential-venv.
set -euo pipefail

seed="${1:-9535}"
documents="${2:-200}"
queries="${3:-10}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
venv="$root/build/differential-venv"
cases="$root/jsonpath-core/build/differential/cases.json"
expected="$root/jsonpath-core/src/test/resources/differential/expected.json"

python="${PYTHON:-python3}"
if [ ! -x "$venv/bin/python" ]; then
  "$python" -m venv "$venv"
fi
"$venv/bin/pip" install --quiet --require-virtualenv -r "$root/scripts/differential/requirements.txt"

"$root/gradlew" -p "$root" --quiet :jsonpath-core:generateDifferentialCases \
  "-PdifferentialOut=$cases" "-PdifferentialSeed=$seed" \
  "-PdifferentialDocuments=$documents" "-PdifferentialQueries=$queries"
"$venv/bin/python" "$root/scripts/differential/evaluate.py" "$cases" "$expected"
echo "Wrote $expected. Run ./gradlew :jsonpath-core:test --tests '*DifferentialTest*' to compare."
