#!/usr/bin/env bash
set -euo pipefail

# Run inside the repository's java21 Nix shell. sbtix's bundled bootstrap lock
# targets its own sbt version, so resolve this project's sbt runtime explicitly.
sbtix-gen
bootstrap_dir="$(mktemp -d)"
trap 'rm -rf "$bootstrap_dir"' EXIT
mkdir -p "$bootstrap_dir/project"
cp project/build.properties "$bootstrap_dir/project/build.properties"
cp nix/sbt-bootstrap.sbt "$bootstrap_dir/build.sbt"
(
  cd "$bootstrap_dir"
  sbtix-gen
)
python3 scripts/lock_sbt_bootstrap.py "$bootstrap_dir" nix/sbt-bootstrap.json
