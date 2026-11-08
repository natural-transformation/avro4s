# PR 17 schema derivation evidence

Measured on 8 November 2026 in [run 42479681317](https://github.com/natural-transformation/avro4s/actions/runs/42479681317).
The baseline is test-only commit `0df287d82ed1fd2c893c86ff503a0a626fae6275`;
the candidate is `35f1778b09a5a48a40e2f636de16bdc88044d6b5`.
Later review fixes change build/audit tooling, not these Scala implementation paths.

The fixture derives a six-level graph with repeated leaf records on every
invocation. `deriveRaw` obtains the in-memory `SchemaFor` graph; `deriveExported`
uses `AvroSchema`. The candidate validates the completed exported graph once
instead of validating every nested case class during composition. Fixture setup
checks that the exported schema round trips through JSON.

| JDK | Operation | Baseline µs/op | Candidate µs/op | Lower mean time | Allocated bytes/op |
| --- | --- | ---: | ---: | ---: | ---: |
| 21 | `deriveExported` | 22.73 ± 0.27 | 19.87 ± 0.43 | 12.6% | 95712 → 88243 |
| 21 | `deriveRaw` | 22.91 ± 0.50 | 18.76 ± 0.27 | 18.1% | 95772 → 86888 |
| 25 | `deriveExported` | 28.32 ± 0.66 | 25.16 ± 0.53 | 11.2% | 93488 → 86376 |
| 25 | `deriveRaw` | 28.64 ± 1.08 | 23.93 ± 0.55 | 16.4% | 93488 → 84574 |

Values after ± are JMH's reported 99.9% confidence-interval half-widths. Each
measurement uses two forks, three one-second warmups, five one-second measurement
iterations, `-prof gc`, and a fixed 512 MiB heap. Both revisions use the same
Scala/dependency versions and benchmark compiler inline limit (`256`). The
comparison workflow sets that limit explicitly on the baseline too.

Each JDK's before/after pair ran sequentially on the same GitHub-hosted runner.
Java 21 used Temurin 21.0.13; Java 25 used OpenJDK 25.0.5.0.3. The JDK pairs ran on
separate runners: **do not compare Java 21 against Java 25 using this table**.
Shared-host scheduling and run order can influence timings. These results apply
to this fixture and do not establish a general codec throughput improvement.
Raw per-fork measurements and allocation results are in the adjacent JSON files.

Correctness evidence precedes the fix: [baseline run 42478345214](https://github.com/natural-transformation/avro4s/actions/runs/42478345214)
failed five new core schema tests, the new Kafka binary compatibility test, and
two inventory identification tests. The revised Scala suite contains 472 tests,
including binary round trips without renaming tuple/fixed schemas and validation
of exported union/tuple/collection graphs and explicit data-container schemas.

Reproduce the comparison with the manual `Schema derivation comparison` workflow,
or run the following inside each checkout's matching Java 21/25 Nix shell:

```sh
sbt -batch 'set benchmarks / Compile / scalacOptions += "-Xmax-inlines:256"' \
  'benchmarks/Jmh/run -wi 3 -i 5 -w 1s -r 1s -f 2 -prof gc -rf json -rff target/schema-benchmark.json benchmarks.performance.SchemaDerivationBenchmarks.*'
```
