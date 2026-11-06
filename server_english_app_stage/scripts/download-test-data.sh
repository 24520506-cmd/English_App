#!/usr/bin/env bash
set -euo pipefail

drive_url="${1:-https://drive.google.com/drive/folders/1zy75qw7ti7PcBktV8m-ScjaylSJ0U2af}"
output_dir="${2:-../test-data/drive}"
tool_dir="$(mktemp -d)"
trap 'rm -rf -- "$tool_dir"' EXIT

python3 -m venv "$tool_dir/venv"
"$tool_dir/venv/bin/pip" install --quiet gdown
mkdir -p "$output_dir"
"$tool_dir/venv/bin/gdown" "$drive_url" -O "$output_dir"
find "$output_dir" -type d -exec chmod a+rx {} +
find "$output_dir" -type f -exec chmod a+r {} +

printf 'Downloaded lesson data to %s\n' "$output_dir"
