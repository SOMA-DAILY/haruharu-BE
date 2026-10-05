#!/usr/bin/env python3
"""Check shared instruction discovery and execute both hosts' hooks in disposable repos."""

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import tomllib
import unittest


ROOT = Path(__file__).resolve().parents[2]
SKILLS = {"bdd-implement", "create-issue", "create-pr", "new-domain", "verify-api"}
AGENTS = {"domain-reviewer", "java-reviewer", "security-reviewer", "java-build-resolver"}


class DiscoveryTest(unittest.TestCase):
    def test_shared_docs_can_be_tracked_while_private_docs_stay_ignored(self):
        for path, status in (("docs/engineering/product.md", 1), ("docs/private-not-tracked.md", 0)):
            result = subprocess.run(["git", "check-ignore", "--no-index", "--quiet", path], cwd=ROOT)
            self.assertEqual(result.returncode, status, path)

    def test_shared_rules_and_product_context_exist(self):
        for name in ("product", "architecture", "bdd-workflow", "testing", "code-style",
                     "exception-handling", "git-conventions", "modification-policy", "secret-policy"):
            with self.subTest(name=name):
                self.assertTrue((ROOT / "docs/engineering" / f"{name}.md").is_file())

    def test_canonical_instructions_do_not_depend_on_claude(self):
        files = [ROOT / "AGENTS.md"]
        files += list((ROOT / ".agents/skills").rglob("*.md"))
        files += list((ROOT / ".codex/agents").glob("*.toml"))
        files += list((ROOT / "docs/engineering").rglob("*.md"))
        for path in files:
            # Backticked paths identify actual file dependencies, not names of supported hosts.
            for target in re.findall(r"`([^`]+)`", path.read_text()):
                with self.subTest(path=path, target=target):
                    self.assertFalse(target.startswith((".claude/", "CLAUDE.md")))

    def test_claude_entry_imports_shared_entry(self):
        imports = re.findall(r"^@([^\s]+)$", (ROOT / "CLAUDE.md").read_text(), re.MULTILINE)
        self.assertIn("AGENTS.md", imports)
        for target in imports:
            self.assertTrue((ROOT / target).is_file())

    def test_skill_adapters_link_to_canonical_skills(self):
        discovered = {p.parent.name for p in (ROOT / ".agents/skills").glob("*/SKILL.md")}
        self.assertEqual(discovered, SKILLS)
        for name in SKILLS:
            canonical = ROOT / ".agents/skills" / name / "SKILL.md"
            adapter = ROOT / ".claude/skills" / name / "SKILL.md"
            links = re.findall(r"\]\(([^)]+)\)", adapter.read_text())
            resolved = {(adapter.parent / target).resolve() for target in links}
            self.assertIn(canonical.resolve(), resolved, name)

    def test_reviewers_reference_shared_contracts(self):
        discovered = set()
        for path in (ROOT / ".codex/agents").glob("*.toml"):
            agent = tomllib.loads(path.read_text())
            name = agent["name"]
            discovered.add(name)
            contract = f"docs/engineering/reviews/{name}.md"
            self.assertTrue((ROOT / contract).is_file(), name)
            self.assertIn(contract, agent["developer_instructions"])
            self.assertIn(contract, (ROOT / ".claude/agents" / f"{name}.md").read_text())
            if name != "java-build-resolver":
                self.assertEqual(agent["sandbox_mode"], "read-only")
        self.assertEqual(discovered, AGENTS)

    def test_codex_bypass_hook_uses_shared_script(self):
        config = json.loads((ROOT / ".codex/hooks.json").read_text())
        commands = [h["command"] for group in config["hooks"]["PreToolUse"] for h in group["hooks"]]
        self.assertTrue(any("scripts/agent-hooks/block-bypass.sh" in command for command in commands))
        self.assertFalse(any(".claude/" in command for command in commands))

    def test_local_markdown_links_resolve(self):
        paths = [ROOT / "AGENTS.md", ROOT / "CLAUDE.md"]
        paths += [ROOT / ".claude/skills" / name / "SKILL.md" for name in SKILLS]
        for folder in (".agents/skills", ".claude/rules", "docs/engineering"):
            paths += list((ROOT / folder).rglob("*.md"))
        for path in paths:
            for target in re.findall(r"\]\(([^)]+)\)", path.read_text()):
                if "://" in target or target.startswith("#"):
                    continue
                target = target.split("#", 1)[0]
                with self.subTest(path=path, target=target):
                    self.assertTrue((path.parent / target).exists())


class HookIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="haruharu-hook-")
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True, capture_output=True)
        for directory in (".claude/hooks", ".codex/hooks", "scripts/agent-hooks"):
            source = ROOT / directory
            if source.is_dir():
                shutil.copytree(source, self.repo / directory, ignore=shutil.ignore_patterns("__pycache__"))
        self.configs = {
            "claude": json.loads((ROOT / ".claude/settings.json").read_text()),
            "codex": json.loads((ROOT / ".codex/hooks.json").read_text()),
        }
        gradlew = self.repo / "gradlew"
        gradlew.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> compile-calls\nexit "${HARNESS_TEST_COMPILE_STATUS:-0}"\n')
        gradlew.chmod(0o755)

    def hook(self, host, event, tool="Edit", inputs=None, compile_status=0):
        payload = {"cwd": str(self.repo), "tool_name": tool, "tool_input": inputs or {}}
        results = []
        env = {**os.environ, "CLAUDE_PROJECT_DIR": str(self.repo), "PYTHONDONTWRITEBYTECODE": "1",
               "HARNESS_TEST_COMPILE_STATUS": str(compile_status)}
        for group in self.configs[host]["hooks"][event]:
            matcher = group.get("matcher", "*")
            if matcher != "*" and not re.fullmatch(matcher, tool):
                continue
            for hook in group["hooks"]:
                results.append(subprocess.run(hook["command"], shell=True, executable="/bin/bash",
                                              input=json.dumps(payload), text=True, cwd=self.repo,
                                              env=env, capture_output=True, timeout=10))
        self.assertTrue(results, (host, event, tool))
        return results

    def assert_codes(self, results, expected):
        self.assertEqual([r.returncode for r in results], expected, [r.stderr for r in results])

    def test_command_allow_and_deny_for_both_hosts(self):
        for host in self.configs:
            for command, status in (("git status", 0), ("git config core.hooksPath .githooks", 0),
                                    ("git commit --no-verify", 2), ("git config core.hooksPath /tmp/empty", 2)):
                with self.subTest(host=host, command=command):
                    self.assert_codes(self.hook(host, "PreToolUse", "Bash", {"command": command}), [status])

    def test_normal_edits_allowed_and_verification_infrastructure_protected(self):
        for host in self.configs:
            for path, status in (("core/src/main/java/Example.java", 0), (".githooks/pre-commit", 2),
                                 (".claude/hooks/stop-gate.sh", 2), (".codex/hooks.json", 2),
                                 ("scripts/agent-hooks/block-bypass.sh", 2)):
                inputs = {"file_path": str(self.repo / path), "new_string": "normal content"}
                with self.subTest(host=host, path=path):
                    self.assert_codes(self.hook(host, "PreToolUse", inputs=inputs), [status])

    def test_jacoco_weakening_still_blocked(self):
        for host in self.configs:
            inputs = {"file_path": str(self.repo / "build.gradle"), "old_string": "jacocoTestReport",
                      "command": "*** Begin Patch\n*** Update File: build.gradle\n- jacocoTestReport\n*** End Patch"}
            self.assert_codes(self.hook(host, "PreToolUse", inputs=inputs), [2])

    def test_yaml_secrets_and_placeholders(self):
        yaml = self.repo / "application-test.yaml"
        for host in self.configs:
            for value, status in (("${HARNESS_TEST_SECRET}", 0), ("synthetic-test-value", 2)):
                yaml.write_text(f"secret: {value}\n")
                self.assert_codes(self.hook(host, "PostToolUse", inputs={"file_path": str(yaml)}), [status])

    def test_java_edits_compile_once_and_clear_pending_state(self):
        source = self.repo / "Example.java"
        source.write_text("class Example {}\n")
        for host in self.configs:
            self.assert_codes(self.hook(host, "PostToolUse", inputs={"file_path": str(source)}), [0])
            pending = self.repo / ".git" / f"{host}-pending-java.list"
            self.assertTrue(pending.is_file())
            self.assert_codes(self.hook(host, "Stop"), [0])
            self.assertFalse(pending.exists())
            self.assert_codes(self.hook(host, "Stop"), [0])
        calls = (self.repo / "compile-calls").read_text().splitlines()
        self.assertEqual(calls, ["-q compileJava compileTestJava"] * 2)

    def test_compile_failure_preserves_existing_retry_limit(self):
        source = self.repo / "Example.java"
        source.write_text("class Example {}\n")
        for host in self.configs:
            self.hook(host, "PostToolUse", inputs={"file_path": str(source)})
            for status in (2, 2, 0):
                self.assert_codes(self.hook(host, "Stop", compile_status=1), [status])
            self.assertFalse((self.repo / ".git" / f"{host}-pending-java.list").exists())

    def test_document_edit_does_not_start_java_build(self):
        doc = self.repo / "README.md"
        doc.write_text("documentation\n")
        for host in self.configs:
            self.assert_codes(self.hook(host, "PostToolUse", inputs={"file_path": str(doc)}), [0])
            self.assert_codes(self.hook(host, "Stop"), [0])
        self.assertFalse((self.repo / "compile-calls").exists())

    def test_shell_hooks_parse(self):
        scripts = list((ROOT / ".claude/hooks").glob("*.sh"))
        scripts += list((ROOT / "scripts/agent-hooks").glob("*.sh"))
        for script in scripts:
            subprocess.run(["bash", "-n", str(script)], check=True, capture_output=True)


if __name__ == "__main__":
    unittest.main()
