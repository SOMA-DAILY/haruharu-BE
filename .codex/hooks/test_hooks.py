#!/usr/bin/env python3
"""Unit tests for Codex hook input parsing and policy decisions."""

from __future__ import annotations

import json
import re
import tomllib
import unittest
from pathlib import Path

from config_protection import block_reason
from hook_common import touched_files
from post_edit import hardcoded_secret_lines


ROOT = Path(__file__).resolve().parents[2]
REQUIRED_SKILLS = {"bdd-implement", "create-issue", "create-pr", "new-domain", "verify-api"}
REQUIRED_AGENTS = {"domain-reviewer", "java-build-resolver", "java-reviewer", "security-reviewer"}


class HookCommonTest(unittest.TestCase):
    def test_extracts_all_apply_patch_paths(self) -> None:
        payload = {
            "tool_input": {
                "command": """*** Begin Patch
*** Update File: core/src/A.java
*** Move to: core/src/B.java
*** Add File: app/src/C.java
*** End Patch"""
            }
        }

        self.assertEqual(
            touched_files(payload),
            ["core/src/A.java", "core/src/B.java", "app/src/C.java"],
        )

    def test_supports_legacy_file_path(self) -> None:
        self.assertEqual(
            touched_files({"tool_input": {"file_path": "core/src/A.java"}}),
            ["core/src/A.java"],
        )


class ConfigProtectionTest(unittest.TestCase):
    def payload(self, root: Path, path: str, body: str = "+safe") -> dict:
        return {
            "cwd": str(root),
            "tool_input": {
                "command": f"*** Begin Patch\n*** Update File: {path}\n{body}\n*** End Patch"
            },
        }

    def test_blocks_codex_hook_modification(self) -> None:
        root = Path.cwd()
        reason = block_reason(self.payload(root, ".codex/hooks.json"))
        self.assertIsNotNone(reason)

    def test_blocks_jacoco_weakening(self) -> None:
        root = Path.cwd()
        reason = block_reason(self.payload(root, "build.gradle", "- jacocoTestReport"))
        self.assertIsNotNone(reason)

    def test_allows_normal_source_edit(self) -> None:
        root = Path.cwd()
        reason = block_reason(self.payload(root, "core/src/main/java/Example.java"))
        self.assertIsNone(reason)


class SecretScanTest(unittest.TestCase):
    def test_detects_literal_and_ignores_placeholder_and_comment(self) -> None:
        content = """password: literal
secret: ${APP_SECRET}
# token: ignored
spring:
  datasource:
    username: app
"""

        self.assertEqual(hardcoded_secret_lines(content), [(1, "password: literal")])


class HarnessDiscoveryInputTest(unittest.TestCase):
    def test_hook_json_and_codex_config_parse(self) -> None:
        hooks = json.loads((ROOT / ".codex/hooks.json").read_text(encoding="utf-8"))
        config = tomllib.loads((ROOT / ".codex/config.toml").read_text(encoding="utf-8"))

        self.assertEqual(set(hooks["hooks"]), {"PreToolUse", "PostToolUse", "Stop"})
        self.assertTrue(config["features"]["hooks"])

    def test_custom_agents_have_required_fields(self) -> None:
        discovered: set[str] = set()
        for path in (ROOT / ".codex/agents").glob("*.toml"):
            agent = tomllib.loads(path.read_text(encoding="utf-8"))
            self.assertTrue(agent.get("name"), path)
            self.assertTrue(agent.get("description"), path)
            self.assertTrue(agent.get("developer_instructions"), path)
            discovered.add(agent["name"])

        self.assertEqual(discovered, REQUIRED_AGENTS)

    def test_skills_have_frontmatter_and_claude_adapters(self) -> None:
        discovered: set[str] = set()
        for path in (ROOT / ".agents/skills").glob("*/SKILL.md"):
            content = path.read_text(encoding="utf-8")
            match = re.match(r"^---\nname: ([^\n]+)\ndescription: ([^\n]+)\n---\n", content)
            self.assertIsNotNone(match, path)
            assert match is not None
            name = match.group(1)
            self.assertTrue(match.group(2).strip(), path)
            self.assertTrue((ROOT / ".claude/skills" / name / "SKILL.md").is_file(), name)
            discovered.add(name)

        self.assertEqual(discovered, REQUIRED_SKILLS)


if __name__ == "__main__":
    unittest.main()
