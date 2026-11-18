#!/usr/bin/env python3
"""Summarize an opt-in context profile; never interpret post-GC heap as live objects."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def summarize(directory, gc_logs):
    workers = [re.fullmatch(r"gc-(\d+)\.log(?:\.\d+)?", path.name) for path in gc_logs]
    if any(worker is None for worker in workers) or len({worker[1] for worker in workers}) != 1:
        raise ValueError("Pass gc-<pid>.log and its rotations from exactly one test worker PID")
    totals = Counter()
    manifest = []
    groups = defaultdict(set)
    stored = set()
    cache_max = 0
    for report in sorted((directory / "test-results").glob("TEST-*.xml")):
        root = ET.parse(report).getroot()
        for key in ("tests", "failures", "errors", "skipped"):
            totals[key] += int(root.get(key, 0))
        manifest.extend((case.get("classname"), case.get("name")) for case in root.iter("testcase"))
        output = "\n".join(root.itertext())
        for context, test_class in re.findall(
                r"(?:Retrieved|Storing) ApplicationContext \[(\d+)\].*?testClass = ([\w.$]+)", output):
            groups[context].add(test_class)
        stored.update(re.findall(r"Storing ApplicationContext \[(\d+)\]", output))
        sizes = re.findall(r"Spring test ApplicationContext cache statistics: \[[^\]\n]*?\bsize = (\d+)", output)
        cache_max = max([cache_max] + list(map(int, sizes)))
    if not manifest or not stored:
        raise ValueError("Missing test results or TRACE context cache records")
    (directory / "test-manifest.json").write_text(json.dumps(sorted(manifest), indent=2) + "\n")

    metrics = (directory / "contexts.tsv").read_text()
    summary = re.search(r"\tsummary\tcreated=(\d+)\tmaxOpen=(\d+)\tpeakHeapBytes=(\d+)", metrics)
    if summary is None:
        raise ValueError("No JVM shutdown summary: the measured test process may not have finished")
    gc = "\n".join(path.read_text() for path in gc_logs)
    event_ids = [int(value) for value in re.findall(r"GC\((\d+)\)", gc)]
    if not event_ids or min(event_ids) != 0:
        raise ValueError("GC history does not start at GC(0); include all rotated logs from this worker")
    pauses = re.findall(r"GC\(\d+\) Pause (Young[^\n]*?|Full[^\n]*?) (\d+)M->(\d+)M\(\d+M\) ([\d.]+)ms", gc)
    if not pauses:
        raise ValueError("No completed Young/Full GC events")
    return {
        **totals,
        "test_context_creations": len(stored),
        "max_cached_contexts": cache_max,
        "boot_context_creations_including_manual_servers": int(summary[1]),
        "max_open_boot_contexts": int(summary[2]),
        "sampled_peak_heap_mib": round(int(summary[3]) / 1024**2, 2),
        "max_heap_after_young_or_full_gc_mib": max(int(after) for _, _, after, _ in pauses),
        "young_or_full_gc_count": len(pauses),
        "full_gc_count": sum(kind.startswith("Full") for kind, _, _, _ in pauses),
        "young_or_full_gc_pause_ms": round(sum(float(ms) for _, _, _, ms in pauses), 3),
        "app_test_task_seconds": round(float((directory / "task-seconds.txt").read_text()), 3),
        "shared_context_groups": sorted(
            (sorted(classes) for classes in groups.values() if len(classes) > 1),
            key=lambda group: (-len(group), group)),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--gc-log", type=Path, nargs="+", required=True,
                        help="All GC logs (including rotations) from this single test worker PID")
    args = parser.parse_args()
    print(json.dumps(summarize(args.directory, args.gc_log), ensure_ascii=False, indent=2))
