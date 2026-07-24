#!/usr/bin/env bash
set -euo pipefail

project_dir="${1:-$PWD}"
pid_file="$project_dir/runtime/api.pid"

if [[ ! -f "$pid_file" ]]; then
  echo "API is not running"
  exit 0
fi

pid="$(cat "$pid_file")"
if [[ ! "$pid" =~ ^[0-9]+$ ]]; then
  echo "Invalid PID file: $pid_file" >&2
  exit 1
fi
if kill -0 "$pid" 2>/dev/null; then
  kill "$pid"
  for _ in {1..20}; do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
fi
rm -f "$pid_file"
echo "API stopped"
