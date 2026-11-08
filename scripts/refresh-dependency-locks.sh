#!/usr/bin/env bash
set -euo pipefail

# Run inside the repository's java21 Nix shell. sbtix's bundled bootstrap lock
# targets its own sbt version, so resolve this project's sbt runtime explicitly.
repo_root="$PWD"
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
# sbtix treats the running sbt's Scala library/reflect JARs as already provided.
# Our standalone bootstrap must pin those too, without relying on a user's cache.
python3 - "$bootstrap_dir/repo.nix" <<'PY'
from pathlib import Path
import re
import subprocess
import sys

path = Path(sys.argv[1])
text = path.read_text()
scala = re.search(r'"scalaVersion" = "([^"]+)";', text).group(1)
entries = []
for module in ["scala-library", "scala-reflect"]:
    relative = f"org/scala-lang/{module}/{scala}/{module}-{scala}.jar"
    key = f"nix-public/{relative}"
    if f'"{key}" =' in text:
        continue
    url = "https://repo1.maven.org/maven2/" + relative
    digest = subprocess.check_output(["nix-prefetch-url", "--type", "sha256", url], text=True).strip()
    entries.append(f'    "{key}" = {{ url = "{url}"; sha256 = "{digest}"; }};')
text = text.replace('  "artifacts" = {', '  "artifacts" = {\n' + "\n".join(entries), 1)
path.write_text(text)
PY
cp "$bootstrap_dir/repo.nix" "$repo_root/sbt-bootstrap-repo.nix"
