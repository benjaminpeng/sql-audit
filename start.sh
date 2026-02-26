#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RUN_DIR="$ROOT_DIR/.run"
BACKEND_LOG="$BACKEND_DIR/backend.log"
FRONTEND_LOG="$FRONTEND_DIR/frontend.log"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"

mkdir -p "$RUN_DIR"

info() { printf '%s\n' "$*"; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
have_cmd() { command -v "$1" >/dev/null 2>&1; }

detect_wsl() {
  [ -n "${WSL_DISTRO_NAME:-}" ] && return 0
  if [ -r /proc/version ] && grep -qi microsoft /proc/version; then
    return 0
  fi
  return 1
}

kill_pidfile() {
  local pid_file="$1"
  local label="$2"
  if [ -f "$pid_file" ]; then
    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then
      info "Stopping previous $label (PID: $pid)..."
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"
  fi
}

cleanup_port() {
  local port="$1"
  if have_cmd lsof; then
    local pids
    pids="$(lsof -ti:"$port" 2>/dev/null || true)"
    if [ -n "$pids" ]; then
      info "Killing process(es) on port $port via lsof..."
      # shellcheck disable=SC2086
      kill -9 $pids 2>/dev/null || true
    fi
    return
  fi

  if have_cmd fuser; then
    if fuser -s "${port}/tcp" 2>/dev/null; then
      info "Killing process(es) on port $port via fuser..."
      fuser -k "${port}/tcp" 2>/dev/null || true
    fi
    return
  fi

  warn "Neither lsof nor fuser is installed; skipping port-based cleanup for :$port."
}

ensure_cmds() {
  local missing=()
  local cmd
  for cmd in "$@"; do
    have_cmd "$cmd" || missing+=("$cmd")
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    die "Missing required commands: ${missing[*]}"
  fi
}

parse_java_major() {
  local out="$1"
  awk -F '[\".]' '
    /version/ {
      if ($2 == "1") print $3;
      else print $2;
      exit
    }' <<<"$out"
}

parse_maven_java_major() {
  local out="$1"
  awk -F ': ' '
    /Java version/ {
      split($2, a, ".");
      if (a[1] == "1") print a[2];
      else print a[1];
      exit
    }' <<<"$out"
}

configure_java_home() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    return 0
  fi

  local os_name
  os_name="$(uname -s)"

  if [ "$os_name" = "Darwin" ] && have_cmd /usr/libexec/java_home; then
    local mac_java_home
    mac_java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [ -n "$mac_java_home" ] && [ -x "$mac_java_home/bin/java" ]; then
      export JAVA_HOME="$mac_java_home"
      return 0
    fi
  fi

  local candidates=(
    /usr/lib/jvm/java-21-openjdk-amd64
    /usr/lib/jvm/java-21-openjdk
    /usr/lib/jvm/temurin-21-jdk-amd64
    /usr/lib/jvm/temurin-21-jdk
    /usr/lib/jvm/temurin-21
  )
  local c
  for c in "${candidates[@]}"; do
    if [ -x "$c/bin/java" ]; then
      export JAVA_HOME="$c"
      return 0
    fi
  done

  if have_cmd readlink && have_cmd java; then
    local java_bin_real
    java_bin_real="$(readlink -f "$(command -v java)" 2>/dev/null || true)"
    if [ -n "$java_bin_real" ]; then
      local inferred
      inferred="$(cd -- "$(dirname -- "$java_bin_real")/.." && pwd -P)"
      if [ -x "$inferred/bin/java" ]; then
        export JAVA_HOME="$inferred"
      fi
    fi
  fi
}

preflight_checks() {
  info "Running environment checks..."
  ensure_cmds java mvn node npm
  configure_java_home

  local java_out java_major node_major maven_out maven_java_major
  java_out="$(java -version 2>&1 || true)"
  java_major="$(parse_java_major "$java_out")"
  [ -n "${java_major:-}" ] || die "Unable to parse Java version. Output: $java_out"
  if [ "$java_major" -lt 21 ]; then
    die "Java 21+ is required, but current java is $java_major."
  fi

  node_major="$(node -p "process.versions.node.split('.')[0]" 2>/dev/null || true)"
  [ -n "${node_major:-}" ] || die "Unable to parse Node.js version."
  if [ "$node_major" -lt 18 ]; then
    die "Node.js 18+ is required, but current node is $node_major."
  fi

  maven_out="$(mvn -v 2>&1 || true)"
  maven_java_major="$(parse_maven_java_major "$maven_out")"
  if [ -n "${maven_java_major:-}" ] && [ "$maven_java_major" -lt 21 ]; then
    die "Maven is using Java $maven_java_major, but Java 21+ is required. Check JAVA_HOME."
  fi

  info "Java:  $java_major"
  info "Node:  $node_major"
  info "Maven: OK"
  if [ -n "${JAVA_HOME:-}" ]; then
    info "JAVA_HOME=$JAVA_HOME"
  fi
}

ensure_frontend_deps() {
  if [ ! -d "$FRONTEND_DIR/node_modules" ] || [ ! -x "$FRONTEND_DIR/node_modules/.bin/vite" ]; then
    info "Installing frontend dependencies (npm install)..."
    (cd "$FRONTEND_DIR" && npm install)
  fi
}

http_check() {
  local url="$1"
  if have_cmd curl; then
    curl -fsS --max-time 2 "$url" >/dev/null
    return $?
  fi
  if have_cmd wget; then
    wget -q --spider "$url"
    return $?
  fi
  return 1
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local pid_file="$3"
  local timeout="${4:-120}"
  local elapsed=0

  while [ "$elapsed" -lt "$timeout" ]; do
    if [ -f "$pid_file" ]; then
      local pid
      pid="$(cat "$pid_file" 2>/dev/null || true)"
      if [ -n "${pid:-}" ] && ! kill -0 "$pid" 2>/dev/null; then
        warn "$label process exited before becoming healthy."
        return 1
      fi
    fi

    if http_check "$url"; then
      return 0
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  warn "Timed out waiting for $label at $url"
  return 1
}

show_recent_logs() {
  local file="$1"
  local label="$2"
  if [ -f "$file" ]; then
    warn "Last lines from $label log ($file):"
    tail -n 40 "$file" >&2 || true
  else
    warn "$label log not found: $file"
  fi
}

print_wsl_notes() {
  if detect_wsl; then
    info "Detected WSL environment: ${WSL_DISTRO_NAME:-unknown distro}"
    info "Tip: scan paths can be Linux paths (/home/... or /mnt/c/...). Windows paths (C:\\...) are auto-converted by the backend."
    case "$ROOT_DIR" in
      /mnt/*)
        warn "Project is running from /mnt/* (Windows filesystem). Build/watch/scan performance may be slower than /home/*."
        ;;
    esac
  fi
}

start_backend() {
  info "Starting SQL Audit Backend..."
  (
    cd "$BACKEND_DIR"
    nohup mvn spring-boot:run >"$BACKEND_LOG" 2>&1 &
    echo $! >"$BACKEND_PID_FILE"
  )
  info "Backend PID: $(cat "$BACKEND_PID_FILE")"
}

start_frontend() {
  info "Starting SQL Audit Frontend..."
  (
    cd "$FRONTEND_DIR"
    nohup npm run dev -- --port 5174 >"$FRONTEND_LOG" 2>&1 &
    echo $! >"$FRONTEND_PID_FILE"
  )
  info "Frontend PID: $(cat "$FRONTEND_PID_FILE")"
}

main() {
  preflight_checks
  ensure_frontend_deps
  print_wsl_notes

  info "Stopping existing services..."
  kill_pidfile "$BACKEND_PID_FILE" "backend"
  kill_pidfile "$FRONTEND_PID_FILE" "frontend"
  cleanup_port 8080
  cleanup_port 5174

  start_backend
  if ! wait_for_http "http://127.0.0.1:8080/api/rules" "backend" "$BACKEND_PID_FILE" 180; then
    show_recent_logs "$BACKEND_LOG" "backend"
    exit 1
  fi

  start_frontend
  if ! wait_for_http "http://127.0.0.1:5174" "frontend" "$FRONTEND_PID_FILE" 120; then
    show_recent_logs "$FRONTEND_LOG" "frontend"
    exit 1
  fi

  info "=================================================="
  info "Project is running"
  info "Frontend: http://localhost:5174"
  info "Backend:  http://localhost:8080"
  info "Logs:"
  info "  - $BACKEND_LOG"
  info "  - $FRONTEND_LOG"
  info "=================================================="
}

main "$@"
