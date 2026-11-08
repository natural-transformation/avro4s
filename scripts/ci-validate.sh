#!/usr/bin/env bash
set -euo pipefail

# Enter the repository's java21 or java25 Nix shell before running this script.
: "${SBT_BOOT:?Set SBT_BOOT to a fresh writable directory outside target}"
java -version
# Regression check: the wrapper must override a conflicting SBT_OPTS boot path.
# The audit verifies the actual classpath and pinned JAR contents afterwards.
SBT_OPTS="${SBT_OPTS:-} -Dsbt.boot.directory=$SBT_BOOT/unmanaged" \
  sbt -batch -Dsbt.server.autostart=false \
  clean test 'benchmarks/Jmh/compile' exportBootstrapClasspath \
  'benchmarks/Jmh/run -wi 0 -i 1 -r 100ms -f 1 -foe true benchmarks.performance.*'

python3 scripts/verify-java-target.py
