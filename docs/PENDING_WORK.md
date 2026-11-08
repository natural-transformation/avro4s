# Pending improvement work

Java 21 and Java 25 are both supported targets. Keep published bytecode and API
usage compatible with Java 21. Supporting 25 does not replace support for 21.

## Performance work

1. **Compare Java 21 and Java 25.** Run the same compiled benchmark artifacts on
   both Nix-managed JDKs, with matching vendor, hardware, heap and measurement
   settings. Include allocation and binary codec measurements. Compatibility
   tests are not evidence of a performance improvement.
2. **Investigate Scala 3 record specialization.** The fixed-schema handwritten
   references in `benchmarks/performance` suggest headroom, not a proven general
   macro speedup. Write semantic tests and benchmarks before implementation;
   preserve runtime schema ordering, renamed/transient fields, defaults, recursive
   types, custom codecs and wrapper serialization. Retain a general fallback if
   specialization cannot support the complete contract.
3. **Broaden benchmark coverage.** Include records with more than 22 fields,
   reference values, strings, options, sealed traits, evolving writer schemas and
   representative streaming payloads. The existing wide fixture has 12 fields.
4. **Retain rejected experiments as deferred work.** Generic iterator collection
   decoding, indexed-sequence encoding and map presizing had regressions or
   inconclusive measurements. Revisit only with a new hypothesis and evidence
   across empty, small and large inputs.

For every Scala optimization, add tests/benchmarks first, measure the unchanged
baseline, implement the candidate, then retain it only when correctness and the
measured tradeoffs justify the change. Keep compiler, dependency and JDK changes
separate from codec changes when attributing performance results.

## Maintenance

- Keep benchmark compilation and fixture smoke checks in the Java 21/25 CI matrix.
  Use a controlled machine for performance comparisons; shared-runner timings
  should not become pass/fail performance thresholds.
- Revisit the commented-out general schema-evolution suite as a separate behavior
  project. The live alternating-schema regression tests do not implement general
  Avro schema resolution.
- Rerun the complete resolved dependency vulnerability scan regularly and when
  dependencies change. A clean scan means no known matching advisories in that
  database at scan time, not immunity from future discoveries.

The historical performance evidence in `benchmarks/results` identifies the old
dependency graph. After dependency updates, collect a fresh baseline before
claiming further performance gains.
