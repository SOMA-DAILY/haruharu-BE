#!/usr/bin/env python3
"""Compile Java changes before Codex is allowed to finish a turn."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

from hook_common import git_dir, read_payload, repo_root


MAX_FAILURES = 3


def remove_if_present(path: Path) -> None:
    try:
        path.unlink()
    except FileNotFoundError:
        pass


def relevant_errors(output: str) -> str:
    lines = [
        line
        for line in output.splitlines()
        if "error:" in line or re.search(r"\.java:\d+", line)
    ]
    if not lines:
        lines = output.splitlines()[-30:]
    return "\n".join(lines[:30])


def main() -> int:
    payload = read_payload()
    root = repo_root(payload)
    if root is None:
        return 0
    repository_git_dir = git_dir(root)
    if repository_git_dir is None:
        return 0

    pending = repository_git_dir / "codex-pending-java.list"
    failure_count = repository_git_dir / "codex-stopgate.count"
    if not pending.is_file() or pending.stat().st_size == 0:
        return 0

    result = subprocess.run(
        [str(root / "gradlew"), "-q", "compileJava", "compileTestJava"],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        remove_if_present(pending)
        remove_if_present(failure_count)
        return 0

    try:
        failures = int(failure_count.read_text(encoding="utf-8").strip()) + 1
    except (FileNotFoundError, ValueError):
        failures = 1
    failure_count.write_text(str(failures), encoding="utf-8")

    output = result.stdout + "\n" + result.stderr
    if failures >= MAX_FAILURES:
        print(
            f"[stop-gate] Compilation failed {failures} consecutive times; releasing the hook "
            "to prevent a loop. Compilation errors still require manual resolution.",
            file=sys.stderr,
        )
        print(relevant_errors(output), file=sys.stderr)
        remove_if_present(pending)
        remove_if_present(failure_count)
        return 0

    print(
        "[stop-gate] Java changed and compilation failed. Fix these errors before finishing:",
        file=sys.stderr,
    )
    print(relevant_errors(output), file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
