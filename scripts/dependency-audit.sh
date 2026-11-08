#!/usr/bin/env bash
set -euo pipefail
: "${SBT_BOOT:?Set SBT_BOOT to a fresh writable directory outside target}"

# Reuse the successful validation's reports. A failed build still gets an audit
# through independent resolution; clean removes any stale classpath report.
if [ ! -f target/dependency-audit/bootstrap-classpath.txt ]; then
  sbt -batch -Dsbt.server.autostart=false update 'benchmarks/update' exportBootstrapClasspath
fi
python3 -m unittest discover -s scripts -p 'test_*.py'
mkdir -p target/dependency-audit
java -version 2> target/dependency-audit/java-version.txt
python3 scripts/dependency_inventory.py --boot "$SBT_BOOT" \
  --classpath target/dependency-audit/bootstrap-classpath.txt \
  --expected "$AVRO4S_SBT_BOOT_SOURCE" --launcher "$AVRO4S_SBT_LAUNCH_JAR" \
  --output target/dependency-audit/osv-scanner.json
osv-scanner scan source \
  --lockfile=osv-scanner:target/dependency-audit/osv-scanner.json \
  --format=json > target/dependency-audit/osv-results.json
