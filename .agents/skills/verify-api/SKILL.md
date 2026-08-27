---
name: verify-api
description: Run the pre-PR compile, tests, coverage, secret scan, live smoke checks, and diff review gate with a fixed report.
---

Read `.claude/skills/verify-api/SKILL.md` from the repository root and follow it
as the canonical workflow. Use the closest available Codex tools for its Bash,
read, search, and reviewer steps. Do not report READY when any required phase is
missing or failing.
