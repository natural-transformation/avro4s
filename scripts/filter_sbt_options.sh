#!/usr/bin/env bash
set -euo pipefail
# Read the entire value: sbtix's offline repository flags span multiple lines.
# EOF returns 1 with a NUL delimiter, after still populating the array.
read -r -d '' -a supplied_options <<< "${SBT_OPTS:-}" || true
for option in "${supplied_options[@]}"; do
  case "$option" in
    -Dsbt.boot.directory=*|-J-Dsbt.boot.directory=*) ;;
    *) printf '%s ' "$option" ;;
  esac
done
