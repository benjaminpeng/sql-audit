#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$SKILL_DIR/../.." && pwd)"
OUTPUT_ROOT="$REPO_ROOT/output/sql-audit"
INLINE_ROOT="$REPO_ROOT/tmp/sql-audit/inline"

usage() {
  cat <<'EOF' >&2
Usage:
  invoke_scan.sh <repo_dir>
  invoke_scan.sh <sql_file.sql>
  invoke_scan.sh --repo-path <repo_dir>
  invoke_scan.sh --sql-file <sql_file.sql>
  invoke_scan.sh --inline-sql "<sql>"
  printf '<sql>' | invoke_scan.sh
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

resolve_java_bin() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    printf '%s\n' "${JAVA_HOME}/bin/java"
    return 0
  fi
  if [[ -x /usr/libexec/java_home ]]; then
    local detected_home
    detected_home="$("/usr/libexec/java_home" 2>/dev/null || true)"
    if [[ -n "$detected_home" && -x "${detected_home}/bin/java" ]]; then
      printf '%s\n' "${detected_home}/bin/java"
      return 0
    fi
  fi
  local maven_java_home
  maven_java_home="$(
    mvn -v 2>/dev/null | sed -n "s/^Java version: .* runtime: //p" | head -n 1
  )"
  if [[ -n "$maven_java_home" && -x "${maven_java_home}/bin/java" ]]; then
    printf '%s\n' "${maven_java_home}/bin/java"
    return 0
  fi
  local java_bin
  java_bin="$(command -v java || true)"
  if [[ -n "$java_bin" ]] && "$java_bin" -version >/dev/null 2>&1; then
    printf '%s\n' "$java_bin"
    return 0
  fi
  return 1
}

resolve_jar() {
  find "$REPO_ROOT/backend/target" -maxdepth 1 -type f -name '*.jar' ! -name '*original*' | head -n 1
}

needs_build() {
  local jar="$1"
  if [[ -z "$jar" || ! -f "$jar" ]]; then
    return 0
  fi
  if [[ "$REPO_ROOT/backend/pom.xml" -nt "$jar" ]]; then
    return 0
  fi
  if find "$REPO_ROOT/backend/src/main" -type f -newer "$jar" | grep -q .; then
    return 0
  fi
  return 1
}

build_backend_if_needed() {
  local jar="$1"
  if needs_build "$jar"; then
    (cd "$REPO_ROOT/backend" && mvn -q -DskipTests package >/dev/null)
  fi
}

MODE=""
INPUT_VALUE=""
INLINE_FILE=""
DISPLAY_INPUT=""

parse_args() {
  if [[ $# -eq 0 ]]; then
    if [[ ! -t 0 ]]; then
      MODE="inline_sql"
      INPUT_VALUE="$(cat)"
      DISPLAY_INPUT="<inline_sql>"
      return
    fi
    usage
    exit 2
  fi

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --repo-path)
        MODE="repo"
        INPUT_VALUE="${2:-}"
        DISPLAY_INPUT="$INPUT_VALUE"
        shift 2
        ;;
      --sql-file)
        MODE="sql_file"
        INPUT_VALUE="${2:-}"
        DISPLAY_INPUT="$INPUT_VALUE"
        shift 2
        ;;
      --inline-sql)
        MODE="inline_sql"
        INPUT_VALUE="${2:-}"
        DISPLAY_INPUT="<inline_sql>"
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        if [[ -d "$1" ]]; then
          MODE="repo"
          INPUT_VALUE="$1"
          DISPLAY_INPUT="$INPUT_VALUE"
        elif [[ -f "$1" && "$1" == *.sql ]]; then
          MODE="sql_file"
          INPUT_VALUE="$1"
          DISPLAY_INPUT="$INPUT_VALUE"
        else
          echo "Unsupported input: $1" >&2
          usage
          exit 2
        fi
        shift
        ;;
    esac
  done

  if [[ -z "$MODE" || -z "$INPUT_VALUE" ]]; then
    usage
    exit 2
  fi
}

cleanup() {
  if [[ -n "$INLINE_FILE" && -f "$INLINE_FILE" ]]; then
    rm -f "$INLINE_FILE"
  fi
}

main() {
  require_cmd mvn
  require_cmd python3

  local java_bin
  java_bin="$(resolve_java_bin)" || {
    echo "Missing usable Java runtime. Set JAVA_HOME to a valid JDK." >&2
    exit 1
  }

  parse_args "$@"
  trap cleanup EXIT

  mkdir -p "$OUTPUT_ROOT" "$INLINE_ROOT"

  if [[ "$MODE" == "inline_sql" ]]; then
    INLINE_FILE="$(mktemp "$INLINE_ROOT/inline-XXXXXX.sql")"
    printf '%s\n' "$INPUT_VALUE" > "$INLINE_FILE"
    INPUT_VALUE="$INLINE_FILE"
  fi

  local run_dir
  run_dir="$(mktemp -d "$OUTPUT_ROOT/run-XXXXXX")"

  local json_out="$run_dir/report.json"
  local markdown_out="$run_dir/report.md"
  local jar
  jar="$(resolve_jar)"
  build_backend_if_needed "$jar"
  jar="$(resolve_jar)"

  if [[ -z "$jar" || ! -f "$jar" ]]; then
    echo "Unable to locate built backend jar under backend/target" >&2
    exit 1
  fi

  local -a cli_args
  cli_args=(
    --spring.main.web-application-type=none
    --sql-audit.cli.enabled=true
    --sql-audit.cli.json-out="$json_out"
    --sql-audit.cli.markdown-out="$markdown_out"
  )

  if [[ "$MODE" == "repo" ]]; then
    cli_args+=(--sql-audit.cli.repo-path="$INPUT_VALUE")
  else
    cli_args+=(--sql-audit.cli.sql-file="$INPUT_VALUE")
  fi

  "$java_bin" -jar "$jar" "${cli_args[@]}" >/dev/null

  python3 - "$MODE" "$DISPLAY_INPUT" "$json_out" "$markdown_out" <<'PY'
import json
import pathlib
import sys

mode, input_value, json_path, markdown_path = sys.argv[1:5]
report = json.loads(pathlib.Path(json_path).read_text())

payload = {
    "ok": True,
    "mode": mode,
    "input": input_value,
    "json_report_path": str(pathlib.Path(json_path).resolve()),
    "markdown_report_path": str(pathlib.Path(markdown_path).resolve()),
    "summary": {
        "total_files": report.get("totalFiles", 0),
        "total_statements": report.get("totalStatements", 0),
        "total_violations": report.get("totalViolations", 0),
        "error_count": report.get("errorCount", 0),
        "warning_count": report.get("warningCount", 0),
        "info_count": report.get("infoCount", 0),
        "limit_reached": report.get("limitReached", False),
    },
    "notices": report.get("notices", []),
}

print(json.dumps(payload, ensure_ascii=False, indent=2))
PY
}

main "$@"
