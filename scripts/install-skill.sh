#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="$REPO_ROOT/skills/sql-audit-scan"
CODEX_HOME="${CODEX_HOME:-$HOME/.codex}"
TARGET_DIR="$CODEX_HOME/skills/sql-audit-scan"

mkdir -p "$CODEX_HOME/skills"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "Skill source not found: $SOURCE_DIR" >&2
  exit 1
fi

if [[ -e "$TARGET_DIR" && ! -L "$TARGET_DIR" ]]; then
  echo "Target exists and is not a symlink: $TARGET_DIR" >&2
  echo "Remove it manually, then rerun this installer." >&2
  exit 1
fi

if [[ -L "$TARGET_DIR" ]]; then
  rm -f "$TARGET_DIR"
fi

ln -s "$SOURCE_DIR" "$TARGET_DIR"
echo "Installed sql-audit-scan -> $TARGET_DIR"
