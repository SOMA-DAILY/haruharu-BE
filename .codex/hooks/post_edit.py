#!/usr/bin/env python3
"""Track Java edits and detect hardcoded YAML secrets immediately."""

from __future__ import annotations

import re
import sys
from pathlib import Path

from hook_common import absolute_path, git_dir, read_payload, relative_path, repo_root, touched_files


SECRET_PATTERN = re.compile(
    r"^\s*(password|secret|token|api.?key|credentials|private.?key)\s*:",
    re.IGNORECASE,
)


def hardcoded_secret_lines(content: str) -> list[tuple[int, str]]:
    findings: list[tuple[int, str]] = []
    for number, line in enumerate(content.splitlines(), start=1):
        if line.lstrip().startswith("#") or "${" in line:
            continue
        if SECRET_PATTERN.search(line):
            findings.append((number, line))
    return findings


def append_java_edit(state_file: Path, path: str) -> None:
    state_file.parent.mkdir(parents=True, exist_ok=True)
    with state_file.open("a", encoding="utf-8") as handle:
        handle.write(path + "\n")


def main() -> int:
    payload = read_payload()
    root = repo_root(payload)
    if root is None:
        return 0
    repository_git_dir = git_dir(root)
    if repository_git_dir is None:
        return 0

    secret_findings: list[str] = []
    for value in touched_files(payload):
        relative = relative_path(root, value)
        if relative is None:
            continue
        path = absolute_path(root, value)

        if relative.endswith(".java"):
            append_java_edit(repository_git_dir / "codex-pending-java.list", relative)

        name = path.name.lower()
        if not (name.startswith("application") and name.endswith((".yml", ".yaml"))):
            continue
        if not path.is_file():
            continue
        for line_number, line in hardcoded_secret_lines(path.read_text(encoding="utf-8")):
            secret_findings.append(f"{relative}:{line_number}: {line}")

    if not secret_findings:
        return 0

    print("[secret-scan] Hardcoded secret-like YAML values detected:", file=sys.stderr)
    for finding in secret_findings:
        print(finding, file=sys.stderr)
    print("Use an ${ENV_VAR} placeholder as required by secret-policy.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
