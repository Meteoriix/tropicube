#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
frontend="$repo_root/tools/language-editor/frontend"
backend="$repo_root/tools/language-editor/backend"
runtime="$repo_root/tools/language-editor/.runtime"
pid_file="$runtime/editor.pid"
output_log="$runtime/editor.log"
jar="$backend/target/tropicube-language-editor.jar"
action="${1:-start}"
browser_option="${2:-}"
port="${TROPICUBE_LANGUAGE_EDITOR_PORT:-8765}"
address="http://127.0.0.1:$port"

if [[ ! "$port" =~ ^[0-9]+$ ]] || ((port < 1 || port > 65535)); then
  echo "TROPICUBE_LANGUAGE_EDITOR_PORT doit être un port compris entre 1 et 65535." >&2
  exit 1
fi

# Git Bash exposes Windows process identifiers differently from native Java.
# Delegate there to the PowerShell launcher so status and stop use the real PID.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    powershell_launcher="powershell.exe"
    command -v pwsh.exe >/dev/null 2>&1 && powershell_launcher="pwsh.exe"
    windows_script="$(cygpath -w "$repo_root/language-editor.ps1")"
    windows_arguments=(-NoProfile -ExecutionPolicy Bypass -File "$windows_script" "$action")
    [[ "$browser_option" == "--no-browser" ]] && windows_arguments+=(-NoBrowser)
    exec "$powershell_launcher" "${windows_arguments[@]}"
    ;;
esac

editor_pid() {
  [[ -f "$pid_file" ]] || return 1
  local pid
  pid="$(tr -d '[:space:]' < "$pid_file")"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null || return 1
  local command
  command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  [[ "$command" == *tropicube-language-editor.jar* ]] || return 1
  printf '%s' "$pid"
}

cleanup_stale_pid() {
  if [[ -f "$pid_file" ]] && ! editor_pid >/dev/null; then
    rm -f -- "$pid_file"
  fi
}

build_editor() {
  if [[ ! -d "$frontend/node_modules" ]]; then
    npm --prefix "$frontend" ci
  fi
  npm --prefix "$frontend" run build
  bash "$repo_root/mvnw" -q -f "$backend/pom.xml" package
}

open_browser() {
  [[ "$browser_option" != "--no-browser" ]] || return 0
  if command -v xdg-open >/dev/null 2>&1; then
    nohup xdg-open "$address" >/dev/null 2>&1 &
  elif command -v open >/dev/null 2>&1; then
    nohup open "$address" >/dev/null 2>&1 &
  fi
}

mkdir -p -- "$runtime"
cleanup_stale_pid

case "$action" in
  status)
    if pid="$(editor_pid)"; then
      echo "Éditeur de langues actif (PID $pid) : $address"
    else
      echo "Éditeur de langues arrêté."
    fi
    ;;
  stop)
    if ! pid="$(editor_pid)"; then
      echo "Éditeur de langues déjà arrêté."
      exit 0
    fi
    kill "$pid"
    for _ in {1..40}; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.25
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -KILL "$pid"
    fi
    rm -f -- "$pid_file"
    echo "Éditeur de langues arrêté."
    ;;
  foreground)
    if editor_pid >/dev/null; then
      echo "L'éditeur de langues est déjà actif : $address" >&2
      exit 1
    fi
    build_editor
    exec java -jar "$jar"
    ;;
  start)
    if pid="$(editor_pid)"; then
      echo "Éditeur de langues déjà actif (PID $pid) : $address"
      open_browser
      exit 0
    fi
    build_editor
    nohup java -jar "$jar" --no-browser >"$output_log" 2>&1 </dev/null &
    pid=$!
    printf '%s\n' "$pid" > "$pid_file"

    ready=false
    for attempt in {1..40}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        break
      fi
      if command -v curl >/dev/null 2>&1 && curl --fail --silent --max-time 1 "$address/api/state" >/dev/null; then
        ready=true
        break
      fi
      if ! command -v curl >/dev/null 2>&1 && ((attempt >= 4)); then
        ready=true
        break
      fi
      sleep 0.25
    done
    if [[ "$ready" != true ]]; then
      kill "$pid" 2>/dev/null || true
      rm -f -- "$pid_file"
      echo "L'éditeur n'a pas démarré sur $address. Consultez $output_log." >&2
      [[ -f "$output_log" ]] && tail -n 20 "$output_log" >&2
      exit 1
    fi

    open_browser
    echo "Éditeur de langues démarré en arrière-plan (PID $pid) : $address"
    echo "Arrêt : ./language-editor.sh stop"
    ;;
  *)
    echo "Usage : $0 [start|stop|status|foreground] [--no-browser]" >&2
    exit 2
    ;;
esac
