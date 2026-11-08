#!/usr/bin/env bash
set -euo pipefail

# No production keys, repository credentials or remote uploads are used.
publish_scratch="$(mktemp -d)"
trap 'gpgconf --kill gpg-agent; rm -rf "$publish_scratch"' EXIT
export GNUPGHOME="$publish_scratch/gnupg"
mkdir -m 700 "$GNUPGHOME"
gpg --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'avro4s CI <ci@example.invalid>' rsa2048 sign 0
export RELEASE_VERSION=0.0.0-ci
sbt -batch -Dsbt.server.autostart=false \
  "set every publishTo := Some(Resolver.file(\"ci-smoke\", file(\"$publish_scratch/repository\")))" \
  'set every useGpg := true' publishSigned
python3 - "$publish_scratch/repository" <<'PY'
from pathlib import Path
import subprocess
import sys
root = Path(sys.argv[1])
for module in ("avro4s-core_3", "avro4s-cats_3", "avro4s-kafka_3"):
    directory = root / "com" / "natural-transformation" / module / "0.0.0-ci"
    for suffix in (".jar", ".pom", "-sources.jar", "-javadoc.jar"):
        artifact = directory / (module + "-0.0.0-ci" + suffix)
        if not artifact.is_file():
            raise RuntimeError("Missing published artifact: " + str(artifact))
        subprocess.run(["gpg", "--batch", "--verify", str(artifact) + ".asc", str(artifact)], check=True)
print("Verified signatures for all 12 published artifacts")
PY
