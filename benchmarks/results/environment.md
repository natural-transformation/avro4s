# Codec experiment environment

- Date: 7 November 2026.
- OS: Darwin 27.0.0, arm64. The sandbox denied the CPU-model `sysctl` query.
- JDK: Nix-installed Azul Zulu 21.0.11+10-LTS, aarch64.
- sbt: Nix-installed 1.10.7; Scala: 3.3.7; Scala library: 2.13.16; JMH: 1.37.
- The reviewer reported Zulu JDK 21.0.8 from `nix develop`; this session still
  cannot access the Nix cache/daemon. All recorded benchmark runs, including the
  review follow-up, use the same explicit Nix-store JDK 21.0.11+10 below.
  Reproductions on another JDK must rerun both baseline and candidate.
- JVM arguments: `-Xms512m -Xmx512m`; default collector; one benchmark thread.
- Baseline production source: `e4e7a78a`.
- The baseline and candidate use identical new benchmark sources and the same
  benchmark build fix: remove unused `sbt-jmh-extras` and manual JMH overrides,
  letting the pinned sbt-jmh 0.4.7 plugin provide JMH 1.37 consistently.
- Measured runs: 2 JVM forks, 3 x 500 ms warmup, 5 x 500 ms measurement,
  `-prof gc`, `-foe true`, JSON output. Baseline and candidate run sequentially.
- Smoke runs use shorter iterations and are not performance evidence.

## Build execution within the sandbox

`nix develop` cannot access the Nix cache/daemon in this session. Validation uses
already installed Nix packages and cached Maven dependencies, with sbt's writable
boot/global/Ivy directories under `/tmp/avro4s-validation`. No sandbox permissions
were changed. Both revisions use the same tools.

```sh
mkdir -p /tmp/avro4s-validation/{boot,global,ivy}
cp -R "$HOME/.sbt/boot/scala-2.12.20" /tmp/avro4s-validation/boot/

task_sbt=/nix/store/7mhrb0a1mqcj78zr15fbr6m3yxd11dws-sbt-1.10.7/bin/sbt
task_jdk=/nix/store/6fnzgvlmqjx18ysfalh4pgrwvn2306m0-zulu-ca-jdk-21.0.11

"$task_sbt" --java-home "$task_jdk" \
  -Dsbt.boot.directory=/tmp/avro4s-validation/boot \
  -Dsbt.global.base=/tmp/avro4s-validation/global \
  -Dsbt.ivy.home=/tmp/avro4s-validation/ivy \
  -Dsbt.server.autostart=false -Dsbt.server.forcestart=true \
  -Dsbt.supershell=false 'set ThisBuild / offline := true' \
  'benchmarks/Jmh/run -wi 3 -w 500ms -i 5 -r 500ms -f 2 -prof gc -foe true -rf json -rff /Users/zhen/workspace/avro4s/benchmarks/results/candidate-reviewed.json -e .*CollectionCodecBenchmarks.*To.* benchmarks.performance.*'
```

The `forcestart` property permits sbt's CLI to continue if its optional boot socket
cannot be created; `autostart=false` keeps its command server disabled. This
behavior was checked in the cached sbt 1.10.7 source. On an unrestricted machine,
the repository's ordinary `nix develop --command sbt ...` command is sufficient.

The same sbt invocation also ran `test`, the focused performance suites,
`benchmarks/Jmh/compile`, and the smoke runs. The baseline is a `git archive`
snapshot with the new tests/benchmarks and benchmark build fix copied in.
The result path must be absolute or relative to the `benchmarks` subproject,
which is the forked JMH process's working directory.

## Correctness evidence

| Check | Result |
| --- | --- |
| Reviewed candidate `avro4s-core/testOnly com.sksamuel.avro4s.codecs.*` | 31 passed |
| Reviewed candidate `test` | 462 passed: core 453, Cats 3, Kafka 6 |
| Candidate `benchmarks/Jmh/compile` | Passed, including generated Java harnesses |
| Original baseline focused suites (25 tests) | 20 passed, exactly 5 expected failures |
| Expanded codec suites before transient cache annotations | 29 passed, 2 failed with NotSerializableException |
| Original production source with all 31 current codec tests | 22 passed, exactly 9 expected failures |

The original baseline failures are the four once-per-wrapper preparation assertions and
the alternating-schema regression: `Pair(4004, 3003)` instead of
`Pair(3003, 4004)`. The six later tests cover cold/warm wrapper serialization,
non-serializable prepared state with lazy restoration, and preparation retries.
Their two prepared-state serialization failures were observed before the
transient annotations were added, then all 31 focused tests passed. The expanded
suite on the original production snapshot has nine expected failures: the five
original assertions plus the two restoration and two retry assertions that
require caching after the next successful preparation. Those four fail on
preparation counts, not on serialization or retry exceptions.

## Source and run provenance

- `baseline.json`: 79 cases, original production source at `e4e7a78a`, with the
  original 25 regression tests, unchanged benchmark sources and shared benchmark
  build configuration. The later test-package rename/additions do not change
  benchmark fixtures or production dependencies.
- `candidate-initial.json.gz`: 79 cases, including rejected iterator collection
  decoders, indexed-sequence encoders, a generic array loop and always-presized
  maps. `candidate-initial.patch` and `candidate-initial.sha256` identify this stage.
- `candidate-array-refinement.json.gz`: 44 cases, collection decoder changes reverted
  and primitive array encoding restored to `Array.map`; indexed-sequence encoding
  and always-presized maps were still present. `candidate-array-refinement.patch`
  records this intermediate production/build diff.
- `candidate-guarded-map.json.gz`: 44 cases, including original iterable encoders and
  map presizing only above 12 entries. The full tests and benchmark compilation
  passed immediately before this run. The 39 non-map-encoding cases measure the
  retained code paths. The five map-encoding cases are rejected experimental data.
  `candidate-guarded-map.patch` and `candidate-guarded-map.sha256` identify the
  compiled sources for this run. The subsequent source-only map revert occurred
  after its map-encoding measurements, without recompiling the running JVMs.
- `candidate-map-control.json.gz`: five map-encoding controls after reverting only
  MapEncoder to the baseline implementation. Full tests and JMH compilation were
  rerun before this measurement, with all other production code unchanged.
- `candidate-before-review.sha256`: source hashes for the pre-review candidate,
  including its old `performance` test paths. This is a historical manifest,
  not a checksum check for the current tree.
- `candidate-reviewed.json`: fresh 44-case run after the review changes, using
  transient wrapper caches, Magnolia's array overload, and no map builder size
  hint. The benchmark source files and build configuration are unchanged.
- `candidate.sha256`: SHA-256 hashes of the six final production files, shared
  build configuration, three benchmark files and three test files in `codecs`.
  These identify the final reviewed tree.
- `candidate-reviewed-measured.sha256`: exact source hashes at measurement time.
  After the run, only a decoder comment was clarified to describe the derived
  ProductCaseClass overload accurately. Recompilation and `javap -c -p` confirmed
  unchanged executable RecordDecoder bytecode. No further timing run was needed.
- [candidate-reviewed-comment.patch](candidate-reviewed-comment.patch): the exact
  one-line edit from measured to final source. Reconstructing the measured text
  by reversing that edit matches the decoder's hash in
  `candidate-reviewed-measured.sha256`; the other 13 source hashes are identical.
  `git apply --reverse --check benchmarks/results/candidate-reviewed-comment.patch`
  verifies its applicability from the repository root without changing files.

The current comparison uses all 44 cases from `candidate-reviewed.json` against
matching baseline methods and sizes. The 35 omitted collection-decoder cases
have no production changes. Earlier comparisons combined 39 retained-path rows
from the guarded-map run with five separate map controls; those measurements
remain historical data, not the current candidate evidence. No score combines
samples across runs.

The original tests, benchmark sources and build configuration were byte-compared
between the initial snapshots. The reviewed benchmark sources/build configuration
remain identical to that baseline; the test suites were subsequently renamed and
expanded as described above.

The four historical files ending in `.json.gz` are lossless gzip archives of
the original JMH JSON. Compression used a fixed zero timestamp, and decompressed
bytes were compared with the originals before replacing the uncompressed files.
Combined size fell from 1,227,357 to 130,198 bytes. The guarded-map and map-control
runs were compressed after round two of review: 350,313 to 37,836 bytes, retaining
all 44 and five cases respectively. Baseline and current-candidate JSON stay
uncompressed for direct comparison.

The historical final-map-control benchmark used the same invocation and measurement flags as
above, replacing the last argument with
`benchmarks.performance.MapCodecBenchmarks.mapEncode` and writing
`candidate-map-control.json` (now archived as `.json.gz`). The full `test` task and `benchmarks/Jmh/compile`
ran before that command in the same sbt session. All 456 tests passed, JMH
compilation passed, and all five benchmark cases completed successfully.
`javap -c -p` output for the baseline and final `MapEncoder` classes was also
compared and is identical; small-map allocation variation is discussed in the
comparison report.


## Review follow-up verification

The installed Magnolia 1.3.20 base CaseClass bytecode has an array overload that
wraps ArraySeq and delegates to the Seq overload, as the review observed.
However, CaseClassDerivation.ProductCaseClass overrides both methods: the Seq
overload calls `Seq.toArray` and then its array overload; the array overload
calls `Tuple.fromArray` and `Mirror.Product.fromProduct` directly. Calling the
array overload therefore also skips the Seq wrapper and an additional array
copy in the derived codecs. The fresh flat-decoding allocation is 168 B/op,
versus 216 B/op in the pre-review candidate and 248 B/op in the baseline.
The resolved Scala library is 2.13.16, not the 2.13.18 cited in round one;
its MapBuilderImpl and HashMapBuilder both delegate to the default no-op
Builder.sizeHint. These checks support the implementation simplifications; the fresh run captures
the additional decoding allocation benefit.

Round-two ownership verification also inspected the resolved Scala 3.3.7 runtime
bytecode. `Tuple.fromArray` forwards the decoder's `Object[]` to
`scala.runtime.Tuples.fromArray`. For 1–22 fields, it loads the elements into
`TupleN`; for more than 22, it clones the array before constructing `TupleXXL`.
Zero fields use `EmptyTuple`. Thus this path does not retain the supplied field
array. These are shallow element copies, not deep copies of custom-codec values.
The decoder itself never mutates the array after handing it to Magnolia.

The read-only inspection used the Nix JDK above and the compiled dependency
classpath, so it inspected the actual resolved library rather than another
version in the cache:

```sh
task_classpath=$(cat avro4s-core/target/streams/compile/dependencyClasspath/_global/streams/export)
"$task_jdk/bin/javap" -classpath "$task_classpath" -c -p \
  'scala.Tuple$' 'scala.runtime.Tuples$'
```

The full tests and JMH compilation were rerun before the reviewed measurement.
The six added tests have meaningful failure/recovery assertions, including that
restored wrappers prepare lazily once. They do not promise byte-identical
serialization or binary serialization compatibility with older library releases.
