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

## Bugs found in the reference

Found with this test in jsonpath-rfc9535 1.0.0 and 2.0.0 (October 2026), except the last one, which
turned up while analysing the others. All six were reported upstream
([#24](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/24) escapes,
[#25](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/25) shorthand names,
[#26](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/26) trailing comma,
[#27](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/27) booleans) and are fixed in 2.0.1
([changelog](https://github.com/jg-rp/python-jsonpath-rfc9535/blob/main/CHANGELOG.md)); the four that
also affected 1.0.0 are fixed in 1.0.1 as well. Both were released on 2026-10-09. Since 2.0.1 the test
finds no differences, so `known-differences.json` is empty.

| Bug | 1.0.0 | 2.0.0 | Fixed in | RFC 9535 |
| --- | --- | --- | --- | --- |
| `\u0000` to `\u001f` escapes in string literals are rejected, also in the library's own normalized paths | yes | yes | 2.0.1, 1.0.1 ([#24](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/24)) | valid (`non-surrogate`, appendix A) |
| Lower-case hex digits are rejected in some escapes, e.g. `\u00e9` (`\u263a` works) | no | yes | 2.0.1 ([#24](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/24)) | valid (ABNF hex digits are case-insensitive) |
| A trailing comma followed by a blank is accepted: `$[1, ]` | no | yes | 2.0.1 ([#26](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/26)) | invalid |
| `-` is accepted in member-name shorthands: `$.a-b` | yes | yes | 2.0.1, 1.0.1 ([#25](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/25)) | invalid |
| Booleans are ordered like numbers: `1 > false`, `true >= 0` | yes | yes | 2.0.1, 1.0.1 ([#27](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/27)) | false: `<` is only defined for two numbers or two strings (2.3.5.2.2) |
| Characters above U+FFFF are rejected in member-name shorthands: `$.😀` | yes | yes | 2.0.1, 1.0.1 ([#25](https://github.com/jg-rp/python-jsonpath-rfc9535/issues/25)) | valid (`name-first` includes `%xE000-10FFFF`) |

The generator keeps all six covered: string literals use Unicode escapes for control characters and,
now and then, for other characters (hex digits in either case), `😀` is one of the shorthand names, the
damage includes trailing commas and `-` after shorthand names, and filters compare booleans with `<`,
`<=`, `>` and `>=`. Evaluated with 2.0.0, the committed corpus differs on 299 of its 2000 queries.
