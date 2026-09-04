# Haruharu Backend Agent Policy

This file is the Codex entry point for the project harness. `CLAUDE.md` and
`.claude/` remain the shared, canonical policy sources for both Claude Code and
Codex. Read `CLAUDE.md` before planning or changing this repository.

## Product Direction

The current MVP is the operator-managed invite-code model described in
`CLAUDE.md`: administrator-configured participants join a scheduled three-day room, share video and
the whole-room group chat, receive missions, make a final selection, and enter one-to-one chat
after a mutual selection. Automatic matching, coin/IAP, and mandatory PASS are
legacy or deferred behavior unless an issue explicitly targets them.

## Mandatory Execution Gates

Every implementation request must pass these gates in order. Approval from an
earlier conversation does not carry into a new conversation.

1. **Plan**: inspect the code, state scope, approach, risks, and verification;
   then ask whether to create the GitHub issue. Do not edit product or harness
   files yet.
2. **Issue**: after approval, create the GitHub issue from the repository
   template; then ask whether implementation should start.
3. **Implement**: after approval, work on an issue branch, follow the BDD loop,
   run required reviewers, and verify behavior; then ask whether to commit and
   open a PR.
4. **Commit/PR**: only after approval, run the secret-policy checklist, commit,
   push, and create the PR.

Never skip an issue because the user said "implement" or "make it". Never
commit, push, or create a PR without the gate-4 approval.

## Required Rule Loading

Read the rule that applies before the corresponding work:

| Work | Canonical rule |
| --- | --- |
| Any file modification | `.claude/rules/modification-policy.md` |
| Java/package/architecture changes | `.claude/rules/architecture.md` |
| Feature or bug implementation | `.claude/rules/bdd-workflow.md` |
| Tests | `.claude/rules/testing.md` |
| Naming/API response conventions | `.claude/rules/code-style.md` |
| Exceptions | `.claude/rules/exception-handling.md` |
| Branch, commit, or PR work | `.claude/rules/git-conventions.md` |
| Commit or PR preparation | `.claude/rules/secret-policy.md` |

The `paths` metadata in Claude rule files is descriptive for Codex. Apply the
document based on this table even when Codex does not interpret that metadata.

## Workflow Routing

Repository skills are exposed to Codex from `.agents/skills`. In Codex they can
be invoked as `$skill-name`; Claude Code uses `/skill-name`.

- Implementation: `$bdd-implement` (requires an issue)
- Issue creation: `$create-issue`
- New domain scaffolding: `$new-domain`, then `$bdd-implement`
- Pre-PR verification: `$verify-api`
- PR creation: `$create-pr`

The repository-tracked skill source is `.claude/skills`. Codex skill files are
adapters that load that source so the workflow is not duplicated.

## Mandatory Reviewers

Codex custom agents live in `.codex/agents`. Invoke them when these conditions
apply and wait for their result before moving to the next gate:

- New domain scaffolding: `domain-reviewer`
- Three or more changed `.java` files: `java-reviewer`
- OAuth2, JWT, Spring Security, filters, interceptors, or auth/payment input:
  `security-reviewer`
- A Gradle compile/build failure that needs focused diagnosis:
  `java-build-resolver`

Reviewer agents report findings. Do not let read-only reviewers edit code. A
FAIL must be fixed and reviewed again before commit/PR approval is requested.

## Engineering Boundaries

- Modules are `core`, `app`, and `admin`; dependencies flow from `app/admin`
  to `core`, never between `app` and `admin`.
- User controllers and request DTOs live in `app`; shared business, domain, and
  storage code lives in `core`; admin-only domains stay in `admin`.
- Preserve the four-layer domain structure and repository-port DIP described in
  `.claude/rules/architecture.md`.
- Keep changes limited to the active issue. Do not clean up unrelated code.
- Do not delete, disable, or weaken tests, JaCoCo, hooks, pre-commit checks, or
  secret scanning to make verification pass.
- Never use `git --no-verify` or change `core.hooksPath` away from `.githooks`.
- Do not expose secrets, credentials, access tokens, or personal integration
  identifiers in tracked files.

## Verification

Use the narrowest relevant checks during development, then follow
`.claude/skills/verify-api/SKILL.md` before PR preparation. Harness-only changes
must at least validate configuration parsing, skill discovery inputs, hook
allow/deny cases, and existing Claude harness compatibility.
