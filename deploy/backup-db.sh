#!/usr/bin/env bash
set -euo pipefail

project_dir="${1:-$PWD}"
runtime_dir="$project_dir/runtime"
env_file="$runtime_dir/app.env"
backup_dir="$runtime_dir/backups"

if [[ ! -f "$env_file" ]]; then
  echo "Missing runtime environment file: $env_file" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$env_file"
set +a
mkdir -p "$backup_dir"
backup_file="$backup_dir/ai-interview-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
db_name="${MYSQL_DATABASE:-interview}"
mysqldump --no-tablespaces --host="${DB_HOST:-127.0.0.1}" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
  --single-transaction --routines --triggers "$db_name" | gzip -9 > "$backup_file"
gzip -t "$backup_file"
zgrep -q 'Dump completed' "$backup_file"
chmod 600 "$backup_file"
echo "Verified backup: $backup_file"
