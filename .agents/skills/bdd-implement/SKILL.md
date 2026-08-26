---
name: bdd-implement
description: BDD implementation loop for an approved GitHub issue, covering RED, GREEN, verification, and mandatory review gates.
---

Read `.claude/skills/bdd-implement/SKILL.md` from the repository root and follow
it as the canonical workflow. In Codex, interpret `/create-issue`,
`/new-domain`, and `/verify-api` as the corresponding `$` skills. Interpret
Claude `Agent` calls as Codex custom agents from `.codex/agents`. Use available
Codex tools that match the canonical tool intent and keep the gates in
`AGENTS.md` authoritative.
