#!/usr/bin/env bash
set -euo pipefail

# Enter the repository's java21 or java25 Nix shell before running this script.
: "${SBT_BOOT:?Set SBT_BOOT to a fresh writable directory outside target}"
java -version
sbt -batch -Dsbt.boot.directory="$SBT_BOOT" -Dsbt.server.autostart=false \
  clean test 'benchmarks/Jmh/compile' \
  'benchmarks/Jmh/run -wi 0 -i 1 -r 100ms -f 1 -foe true benchmarks.performance.*'

python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/verify-java-target.py
python3 scripts/dependency_inventory.py --boot "$SBT_BOOT" \
  --output target/dependency-audit/osv-scanner.json

# OSV exits nonzero for findings or scan errors. Never suppress either condition.
osv-scanner scan source \
  --lockfile=osv-scanner:target/dependency-audit/osv-scanner.json \
  --format=json > target/dependency-audit/osv-results.json
