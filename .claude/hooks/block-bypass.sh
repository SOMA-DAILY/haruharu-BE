#!/usr/bin/env bash
# Claude input is forwarded unchanged to the shared command policy.
PROJ="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
exec bash "$PROJ/scripts/agent-hooks/block-bypass.sh"
