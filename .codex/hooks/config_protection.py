#!/usr/bin/env python3
"""Block edits that weaken the repository verification harness."""

from __future__ import annotations

import sys
from pathlib import PurePosixPath

from hook_common import patch_text, read_payload, relative_path, repo_root, touched_files


def block_reason(payload: dict) -> str | None:
    root = repo_root(payload)
    if root is None:
        return None

    patch = patch_text(payload).lower()
    for value in touched_files(payload):
        path = relative_path(root, value)
        if path is None:
            continue

        if path.startswith("scripts/agent-hooks/"):
            return "Shared verification hooks are protected infrastructure."
        if path == ".githooks" or path.startswith(".githooks/"):
            return ".githooks/ is verification infrastructure and cannot be edited by the agent."
        if path == ".claude/settings.json" or path.startswith(".claude/hooks/"):
            return "Claude hook configuration is protected; the user must edit it directly."
        if path in {".codex/config.toml", ".codex/hooks.json"} or path.startswith(".codex/hooks/"):
            return "Codex hook configuration is protected; the user must edit it directly."
        if PurePosixPath(path).name == "build.gradle" and "jacoco" in patch:
            return "JaCoCo-related Gradle changes require explicit user handling; fix the code instead."
    return None


def main() -> int:
    reason = block_reason(read_payload())
    if reason is None:
        return 0
    print(f"[config-protection] {reason}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
