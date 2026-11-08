#!/usr/bin/env bash
set -euo pipefail
: "${SBT_BOOT:?Set SBT_BOOT to a fresh writable directory outside target}"

# Standalone audits always resolve afresh. Only CI, immediately after successful
# clean validation in the same checkout, may explicitly reuse its reports.
case "${1:-}" in
  '') reuse_validation=false ;;
  --reuse-validation) reuse_validation=true ;;
  *) echo 'Usage: dependency-audit.sh [--reuse-validation]' >&2; exit 2 ;;
esac
if [ "$reuse_validation" = false ]; then
  sbt -batch -Dsbt.server.autostart=false update 'benchmarks/update' exportBootstrapClasspath
fi
python3 -m unittest discover -s scripts -p 'test_*.py'
mkdir -p target/dependency-audit
java -version 2> target/dependency-audit/java-version.txt
python3 scripts/dependency_inventory.py --boot "$SBT_BOOT" \
  --classpath target/dependency-audit/bootstrap-classpath.txt \
  --expected "$AVRO4S_SBT_BOOT_SOURCE" --launcher "$AVRO4S_SBT_LAUNCH_JAR" \
  --launcher-path target/dependency-audit/launcher-path.txt \
  --output target/dependency-audit/osv-scanner.json
osv-scanner scan source \
  --lockfile=osv-scanner:target/dependency-audit/osv-scanner.json \
  --format=json > target/dependency-audit/osv-results.json
