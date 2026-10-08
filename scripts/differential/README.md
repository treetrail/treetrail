# Differential testing against jsonpath-rfc9535

`DifferentialTest` (in `jsonpath-core`) compares Treetrail with an independent RFC 9535 implementation,
Python's [jsonpath-rfc9535](https://github.com/jg-rp/python-jsonpath-rfc9535), on random documents and
queries. Both must agree on whether a query is valid and, if so, on the selected values and their
normalized paths, in order.

The reference results are checked in, so `./gradlew build` needs no Python:

| File | Content |
| --- | --- |
| `jsonpath-core/src/test/resources/differential/expected.json` | 200 documents with 10 queries each (seed 9535) and the reference's verdicts |
| `jsonpath-core/src/test/resources/differential/known-differences.json` | Queries where the reference is wrong, with the reason |
| `requirements.txt` | The pinned reference version and its dependencies |

About one query in seven is damaged on purpose (a character deleted, doubled or inserted), so that
rejecting invalid queries is compared as well.

## Regenerating

```bash
PYTHON=python3.14 scripts/differential/regenerate.sh  # default seed 9535, 200 documents, 10 queries each
scripts/differential/regenerate.sh 42 500 20          # another seed and size (Python 3.12+ as python3)
```

The test fails for every difference that is not listed in `known-differences.json`, and for a listed
one that no longer occurs. A new difference is a bug on one side: check RFC 9535 and the Compliance Test
Suite, fix Treetrail or list the case with the reference's bug. A freshly generated file can also be
checked without replacing the committed one:

```bash
./gradlew :jsonpath-core:test --tests '*DifferentialTest*' -PdifferentialExpected=/path/to/expected.json
```

## Known bugs of the reference

Found with this test in jsonpath-rfc9535 1.0.0 and 2.0.0 (October 2026). The generator avoids the first
two, which concern escapes it would otherwise produce often; the others are listed in
`known-differences.json`.

| Bug | 1.0.0 | 2.0.0 | RFC 9535 |
| --- | --- | --- | --- |
| `\u0000` to `\u001f` escapes in string literals are rejected, also in the library's own normalized paths | yes | yes | valid (`non-surrogate`, appendix A) |
| Lower-case hex digits are rejected in some escapes, e.g. `é` (`☺` works) | no | yes | valid (ABNF hex digits are case-insensitive) |
| A trailing comma followed by a blank is accepted: `$[1, ]` | no | yes | invalid |
| `-` is accepted in member-name shorthands: `$.a-b` | yes | yes | invalid |
| Booleans are ordered like numbers: `1 > false`, `true >= 0` | yes | yes | false: `<` is only defined for two numbers or two strings (2.3.5.2.2) |
