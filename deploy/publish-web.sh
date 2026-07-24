#!/usr/bin/env bash
set -euo pipefail

project_dir="${1:-$PWD}"
source_dir="$project_dir/frontend/dist/"
target_dir="/var/www/ai-interview-platform/"

if [[ ! -f "${source_dir}index.html" ]]; then
  echo "Missing frontend build output: ${source_dir}index.html" >&2
  exit 1
fi

sudo mkdir -p "$target_dir"
sudo rsync -a --delete --chmod=D755,F644 "$source_dir" "$target_dir"
echo "Frontend published to $target_dir"
