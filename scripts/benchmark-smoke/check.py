"""Checks a short JMH run of QueryBenchmark for large regressions.

Usage: python check.py <jmh-results.json> [minimum ratio]

For every query, divides Jayway JsonPath's time by this library's time from the same run, so that the speed of
the machine cancels out. Fails if a ratio falls below the minimum (default 0.8, that is, this library more than
25 % slower than Jayway). The published ratios are 1.1 to 2.3 (docs/benchmarks.md), so only a large regression
trips the check; a short run on a shared CI machine is too noisy for anything finer. Writes a Markdown table
to $GITHUB_STEP_SUMMARY when it is set.
"""

import json
import os
import sys


def main() -> int:
    results_file = sys.argv[1]
    minimum = float(sys.argv[2]) if len(sys.argv) > 2 else 0.8
    with open(results_file, encoding="utf-8") as f:
        results = json.load(f)

    times = {}
    for result in results:
        benchmark_class, library = result["benchmark"].rsplit(".", 2)[1:]
        if benchmark_class != "QueryBenchmark":
            continue
        query = result["params"]["query"]
        times[(query, library)] = result["primaryMetric"]["score"]

    queries = sorted({query for query, _ in times})
    rows = ["| Query | This library (µs) | Jayway (µs) | Jayway / this |", "| --- | --- | --- | --- |"]
    failures = []
    for query in queries:
        ours = times.get((query, "rfc9535"))
        theirs = times.get((query, "jayway"))
        if ours is None or theirs is None:
            failures.append(f"{query}: missing a result")
            continue
        ratio = theirs / ours
        rows.append(f"| {query} | {ours:.3f} | {theirs:.3f} | {ratio:.2f}× |")
        if ratio < minimum:
            failures.append(f"{query}: Jayway / this library = {ratio:.2f}, below {minimum}")

    report = "\n".join(rows)
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### Benchmark smoke run\n\n" + report + "\n")
    for failure in failures:
        print(f"::error::{failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
