#!/usr/bin/env bash
set -Eeuo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
runtime_dir="$project_dir/runtime"
api_log="$runtime_dir/local-api.log"
web_log="$runtime_dir/local-web.log"
api_pid=""
web_pid=""
api_port=8080
web_port=5173

mkdir -p "$runtime_dir"

say() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

url_ready() {
  curl --fail --silent --max-time 2 "$1" >/dev/null 2>&1
}

port_in_use() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

find_free_port() {
  local preferred="$1"
  local fallback_start="$2"
  if ! port_in_use "$preferred"; then
    printf '%s' "$preferred"
    return
  fi
  for ((candidate = fallback_start; candidate < fallback_start + 20; candidate++)); do
    if ! port_in_use "$candidate"; then
      printf '%s' "$candidate"
      return
    fi
  done
  return 1
}

stop_services() {
  trap - EXIT INT TERM
  say "正在停止本地服务…"
  if [[ -n "$web_pid" ]] && kill -0 "$web_pid" 2>/dev/null; then
    kill "$web_pid" 2>/dev/null || true
  fi
  if [[ -n "$api_pid" ]] && kill -0 "$api_pid" 2>/dev/null; then
    kill "$api_pid" 2>/dev/null || true
  fi
  wait "$web_pid" 2>/dev/null || true
  wait "$api_pid" 2>/dev/null || true
  say "服务已停止。"
}

show_failure() {
  say "$1"
  [[ -f "$api_log" ]] && { printf '\n后端日志末尾：\n'; tail -n 30 "$api_log"; }
  [[ -f "$web_log" ]] && { printf '\n前端日志末尾：\n'; tail -n 20 "$web_log"; }
  exit 1
}

wait_for_url() {
  local url="$1"
  local attempts="$2"
  local label="$3"
  local process_pid="$4"
  for ((index = 1; index <= attempts; index++)); do
    url_ready "$url" && return 0
    kill -0 "$process_pid" 2>/dev/null || show_failure "${label} 启动失败。"
    sleep 1
  done
  show_failure "等待 ${label} 超时。"
}

open_browser() {
  local address="$1"
  if command -v open >/dev/null 2>&1; then
    open "$address"
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$address" >/dev/null 2>&1 || true
  fi
}

printf '\033]0;AI 面试平台 · 本地运行\007'
say "AI 面试平台一键启动"

for required_command in java npm curl lsof; do
  command -v "$required_command" >/dev/null 2>&1 \
    || show_failure "缺少命令：${required_command}。请先安装 Java 17+ 和 Node.js 22+。"
done

api_port="$(find_free_port 8080 18080)" || show_failure "找不到可用的后端端口。"
web_port="$(find_free_port 5173 15173)" || show_failure "找不到可用的前端端口。"
if [[ "$api_port" != "8080" ]]; then
  say "端口 8080 已占用，后端将自动使用 ${api_port}。"
fi
if [[ "$web_port" != "5173" ]]; then
  say "端口 5173 已占用，前端将自动使用 ${web_port}。"
fi

java_version="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
java_major="${java_version%%.*}"
if [[ "$java_major" == "1" ]]; then
  java_major="$(printf '%s' "$java_version" | cut -d. -f2)"
fi
[[ "$java_major" =~ ^[0-9]+$ ]] || show_failure "无法识别 Java 版本：${java_version}"
(( java_major >= 17 )) || show_failure "Java 版本过低：${java_version}，需要 Java 17 或 Java 21。"

java_override=()
if (( java_major < 21 )); then
  java_override=("-Djava.version=$java_major")
  say "检测到 Java ${java_major}，将使用兼容模式启动；生产环境仍建议 Java 21。"
else
  say "检测到 Java ${java_major}。"
fi

if [[ ! -x "$project_dir/frontend/node_modules/.bin/vite" ]]; then
  say "首次运行，正在安装前端依赖…"
  (cd "$project_dir/frontend" && npm ci) || show_failure "前端依赖安装失败。"
fi

say "正在构建后端…"
(cd "$project_dir/backend" && ./mvnw -q "${java_override[@]}" -DskipTests package) \
  || show_failure "后端构建失败。"

: >"$api_log"
: >"$web_log"
trap stop_services EXIT INT TERM

say "正在启动后端…"
(
  cd "$project_dir/backend"
  exec java -jar target/interview-api-0.0.1-SNAPSHOT.jar --server.port="$api_port"
) >>"$api_log" 2>&1 &
api_pid=$!
wait_for_url "http://localhost:$api_port/actuator/health" 90 '后端' "$api_pid"
say "后端已就绪：http://localhost:$api_port"

say "正在启动前端…"
(
  cd "$project_dir/frontend"
  export VITE_API_PROXY_TARGET="http://127.0.0.1:$api_port"
  exec ./node_modules/.bin/vite --host 127.0.0.1 --port "$web_port" --strictPort
) >>"$web_log" 2>&1 &
web_pid=$!
wait_for_url "http://localhost:$web_port" 30 '前端' "$web_pid"

web_address="http://localhost:$web_port"
say "启动成功：$web_address"
say "浏览器即将打开。请保持此窗口运行；按 Control+C 可停止全部服务。"
say "日志：$api_log 和 $web_log"
open_browser "$web_address"

while kill -0 "$api_pid" 2>/dev/null && kill -0 "$web_pid" 2>/dev/null; do
  sleep 2
done
show_failure "检测到服务异常退出。"
