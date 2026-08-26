#!/usr/bin/env python3
"""Shared parsing helpers for repository-local Codex hooks."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


PATCH_PATH_PATTERN = re.compile(
    r"^\*\*\* (?:Add|Update|Delete) File: (.+)$|^\*\*\* Move to: (.+)$",
    re.MULTILINE,
)


def read_payload() -> dict[str, Any]:
    try:
        value = json.load(sys.stdin)
    except (json.JSONDecodeError, OSError):
        return {}
    return value if isinstance(value, dict) else {}


def tool_input(payload: dict[str, Any]) -> dict[str, Any]:
    value = payload.get("tool_input", {})
    return value if isinstance(value, dict) else {}


def patch_text(payload: dict[str, Any]) -> str:
    value = tool_input(payload).get("command", "")
    return value if isinstance(value, str) else ""


def touched_files(payload: dict[str, Any]) -> list[str]:
    """Return paths from Codex apply_patch input or legacy Edit/Write input."""
    inputs = tool_input(payload)
    paths: list[str] = []

    legacy_path = inputs.get("file_path")
    if isinstance(legacy_path, str) and legacy_path:
        paths.append(legacy_path)

    for match in PATCH_PATH_PATTERN.finditer(patch_text(payload)):
        path = match.group(1) or match.group(2)
        if path and path not in paths:
            paths.append(path)
    return paths


def repo_root(payload: dict[str, Any]) -> Path | None:
    cwd_value = payload.get("cwd")
    cwd = Path(cwd_value) if isinstance(cwd_value, str) and cwd_value else Path.cwd()
    result = subprocess.run(
        ["git", "-C", str(cwd), "rev-parse", "--show-toplevel"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    return Path(result.stdout.strip()).resolve()


def git_dir(root: Path) -> Path | None:
    result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--git-dir"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    path = Path(result.stdout.strip())
    return path if path.is_absolute() else (root / path).resolve()


def absolute_path(root: Path, value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (root / path).resolve()


def relative_path(root: Path, value: str) -> str | None:
    try:
        return absolute_path(root, value).relative_to(root).as_posix()
    except ValueError:
        return None
