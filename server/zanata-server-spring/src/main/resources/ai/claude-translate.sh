#!/usr/bin/env sh
#
# Native-client translation shim, bundled in the jar and extracted +
# executed by AnthropicTranslationProvider when "use native client" is on.
#
# Runs the official Claude Code CLI as a first-party client, so a Max/Pro
# subscription token (CLAUDE_CODE_OAUTH_TOKEN) works here even though the
# same token is refused on the raw Messages API. No API billing.
#
# Contract:
#   * Prompt  : read from STDIN (handles arbitrary multi-line content safely).
#   * Model   : $1 (defaults to "sonnet").
#   * Binary  : $CLAUDE_BIN (defaults to "claude" from PATH).
#   * Auth    : $CLAUDE_CODE_OAUTH_TOKEN if exported, else the machine login.
#   * Output  : the translation on STDOUT; non-zero exit on failure.
#
set -eu

MODEL="${1:-sonnet}"
BIN="${CLAUDE_BIN:-claude}"

if ! command -v "$BIN" >/dev/null 2>&1; then
  echo "claude-translate: '$BIN' not found on PATH" >&2
  exit 127
fi

# -p           : single-shot print mode (no interactive session)
# --output-format text : plain translation, no JSON envelope
# --no-session-persistence : don't write a resumable session to disk
# (never pass --bare: it disables OAuth and forces ANTHROPIC_API_KEY)
exec "$BIN" -p \
  --model "$MODEL" \
  --output-format text \
  --no-session-persistence
