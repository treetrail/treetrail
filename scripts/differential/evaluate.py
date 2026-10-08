"""Evaluates the cases of DifferentialCases with jsonpath-rfc9535, the reference to compare against.

Usage: python evaluate.py <cases.json> <expected.json>

Writes, for every document and query, whether the query is valid and, if so, the selected values and
normalized paths. Query errors that RFC 9535 calls invalid (syntax, function types, unknown functions, bad indexes)
count as invalid; the reference's recursion limit is recorded as "limit" and skipped; any other error is
recorded as "error" so that the comparison fails loudly.
"""

import json
import sys
from importlib.metadata import version

import jsonpath_rfc9535 as jsonpath


def evaluate(query, document):
    try:
        nodes = jsonpath.find(query, document)
    except (jsonpath.JSONPathSyntaxError, jsonpath.JSONPathTypeError, jsonpath.JSONPathNameError,
            jsonpath.JSONPathIndexError):
        # Index errors are raised while parsing (leading zeros, out of the I-JSON range): invalid queries.
        return {"query": query, "valid": False}
    except jsonpath.JSONPathRecursionError as e:
        # An implementation limit of the reference, not a verdict on the query; skipped by the comparison.
        return {"query": query, "limit": f"{type(e).__name__}: {e}"}
    except jsonpath.JSONPathError as e:
        return {"query": query, "error": f"{type(e).__name__}: {e}"}
    return {"query": query, "valid": True, "values": nodes.values(), "paths": nodes.paths()}


def main(source, target):
    with open(source, encoding="utf-8") as f:
        cases = json.load(f)
    results = []
    for case in cases["cases"]:
        document = json.loads(case["document"])
        results.append({
            "document": case["document"],
            "results": [evaluate(query, document) for query in case["queries"]],
        })
    with open(target, "w", encoding="utf-8") as f:
        json.dump({
            "reference": f"jsonpath-rfc9535 {version('jsonpath-rfc9535')}",
            "seed": cases["seed"],
            "cases": results,
        }, f, ensure_ascii=False, indent=None, separators=(",", ":"))
        f.write("\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
