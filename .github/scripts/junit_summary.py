#!/usr/bin/env python3
"""Render JUnit XML results into the GitHub Actions step summary.

Both suites already write JUnit XML (Gradle by default, pytest via --junitxml),
so the run's totals and the names of any failures are visible on the summary
page without opening the log or adding a third-party action.

Usage: junit_summary.py <directory-of-xml-files>
"""
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> int:
    results_dir = Path(sys.argv[1])
    if not results_dir.is_dir():
        print(f"::warning::no test results at {results_dir}")
        return 0

    total = failed = skipped = 0
    duration = 0.0
    failures: list[str] = []

    for xml_file in sorted(results_dir.glob("*.xml")):
        for suite in ET.parse(xml_file).iter("testsuite"):
            total += int(suite.get("tests", 0))
            failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
            duration += float(suite.get("time", 0) or 0)
            for case in suite.iter("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    failures.append(f"{case.get('classname', '')}.{case.get('name', '')}")

    passed = total - failed - skipped
    verdict = "❌ failed" if failed else "✅ passed"
    lines = [
        f"### Tests {verdict}",
        "",
        f"| passed | failed | skipped | time |",
        f"|---:|---:|---:|---:|",
        f"| {passed} | {failed} | {skipped} | {duration:.1f}s |",
    ]
    if failures:
        lines += ["", "**Failures**", ""] + [f"- `{name}`" for name in failures[:25]]
        if len(failures) > 25:
            lines.append(f"- …and {len(failures) - 25} more")

    report = "\n".join(lines) + "\n"
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
