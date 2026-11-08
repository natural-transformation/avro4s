#!/usr/bin/env bash
set -euo pipefail
: "${SBT_BOOT:?Set SBT_BOOT to a fresh writable directory outside target}"

# Resolve independently so a failed compatibility test cannot hide scan results.
sbt -batch -Dsbt.boot.directory="$SBT_BOOT" -Dsbt.server.autostart=false \
  update 'benchmarks/update'
python3 -m unittest discover -s scripts -p 'test_*.py'
mkdir -p target/dependency-audit
java -version 2> target/dependency-audit/java-version.txt
python3 scripts/dependency_inventory.py --boot "$SBT_BOOT" \
  --output target/dependency-audit/osv-scanner.json
osv-scanner scan source \
  --lockfile=osv-scanner:target/dependency-audit/osv-scanner.json \
  --format=json > target/dependency-audit/osv-results.json
