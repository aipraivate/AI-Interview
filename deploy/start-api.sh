#!/usr/bin/env bash
set -euo pipefail

project_dir="${1:-$PWD}"
runtime_dir="$project_dir/runtime"
env_file="$runtime_dir/app.env"
pid_file="$runtime_dir/api.pid"
log_file="$runtime_dir/api.log"
jar_file="$project_dir/backend/target/interview-api-0.0.1-SNAPSHOT.jar"

if [[ ! -f "$env_file" ]]; then
  echo "Missing runtime environment file: $env_file" >&2
  exit 1
fi
if [[ ! -f "$jar_file" ]]; then
  echo "Missing application jar: $jar_file" >&2
  exit 1
fi
if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "API is already running with PID $(cat "$pid_file")"
  exit 0
fi

mkdir -p "$runtime_dir"
set -a
# shellcheck source=/dev/null
source "$env_file"
set +a
nohup java -jar "$jar_file" >>"$log_file" 2>&1 &
echo $! >"$pid_file"
echo "API started with PID $(cat "$pid_file")"
