# Scala codec performance

This patch reduces repeated codec preparation and intermediate allocations while
keeping Scala 3.3.7, Avro 1.11.5 and the JDK 21 target unchanged. See
[the measured comparison](results/COMPARISON.md) for per-case timings,
uncertainty, allocation counts and rejected experiments, and
[the environment](results/environment.md) for the exact validation tools.

The tests and benchmark sources were written before the production changes;
additional edge cases were added during review. Baseline execution became
possible later, using a source snapshot of `e4e7a78a` with the same tests and
benchmark harness. The baseline passed 20 of the 25 new tests and failed exactly
the four preparation-count assertions and the alternating-schema regression.
After review follow-up, the candidate passes all 462 tests (453 core, 3 Cats,
6 Kafka), including 31 codec regression tests under `com.sksamuel.avro4s.codecs`.
The six additional tests cover serialization before/after first use, exclusion
of non-serializable prepared state, lazy preparation after restoration, and
retry after a preparation failure. Before making the caches transient, the
two prepared-state serialization tests failed with `NotSerializableException`;
they now pass.

## Changes retained

| Change | Performance evidence | Correctness evidence |
| --- | --- | --- |
| Lazily prepare `ToRecord` / `FromRecord` codecs once per wrapper | `CodecPreparationBenchmarks`, with already-prepared references | Preparation counts, per-value execution, lazy first use, schema isolation, concurrent first use |
| Avoid copying fresh record field arrays (ArraySeq for encoding; Magnolia array overload for decoding) | Flat, wide and nested `RecordCodecBenchmarks`; allocation profiler | Independent encoded outputs, eager primitive reads, empty records, annotations, runtime schema ordering and custom codecs |
| Keep the array produced by `Array.map` instead of also building a List | `CollectionCodecBenchmarks.arrayEncode`, sizes 0/1/8/128/1024 | Ordering, duplicates, custom codecs, nulls, immutable Java view and input snapshots |
| Decode Java maps directly into one Scala map builder | String and Utf8 `MapCodecBenchmarks` | Both key representations, Unicode keys, custom value codecs and input snapshots |
| Check writer-schema identity on every decoded record | Required for safe reuse, measured with record/wrapper/binary decoding | Alternating field orders and missing writer fields after an initial full record |

The explicit wrapping sites receive freshly allocated arrays that the library
never mutates again. Record decoding passes its fresh array directly to
Magnolia's ProductCaseClass implementation, avoiding a sequence wrapper and the
Seq overload's conversion back to an array. The resolved Scala runtime copies
elements into TupleN, or clones the array for TupleXXL; see the bytecode check
in [environment.md](results/environment.md). No caller-owned array is wrapped. Encoded collections remain read-only
Java lists. List, Vector, Seq, Set, map-encoder and collection-decoder
implementations retain their original behavior, including Set collision semantics.

The wrapper cache changes preparation frequency, not value processing. Custom
codecs must return reusable functions; the encoder API already documents this
contract. Preparation is still lazy. The cache is transient, so deserialized
wrappers prepare again on first use instead of serializing the cached function
and any process-local state it captures. This is not a promise of byte-for-byte
or cross-version Java serialization compatibility. The shared wrapper safely initializes its
function once; it does not make an arbitrary custom stateful function thread-safe.
Schemas are assumed stable after preparation, as for directly prepared codecs.

A cached decoder must not remember the first writer schema's layout. The old
`SchemaFieldDecoder.fast` flag did that and could return swapped values. The
per-record identity check preserves positional access for the matching schema
and selects the existing safe field lookup otherwise.

Java maps should have distinct Avro string keys. Unequal keys with identical
`toString` values have ambiguous collision semantics and are not performance
fixtures.

## Experiments not retained

Iterator-based collection decoders and indexed-sequence encoding regressed some
representative inputs. Those changes were reverted. A generic hand-written array
loop was also slower at a mid-size input; the retained implementation uses
Scala's existing `Array.map`, which specializes primitive-array access.
Map presizing showed larger-map gains, but the eight-entry allocation measurements
were unstable across forks. Restricting presizing to larger maps did not resolve
that uncertainty, so map encoding was reverted entirely. The unchanged final
control also reproduced the allocation variation: it is not established that
presizing caused it. This proposal needs more investigation before adoption.
The comparison report preserves the rejected runs rather than hiding them.

Scala 3 macro specialization is not part of this patch. The two
`*DirectReference` benchmarks are hand-written, fixed-schema experiments using
the same child codecs. They retain the baseline encoding representation and do
not implement runtime-schema handling. Their results cannot establish that a
general macro preserves annotations, defaults, recursion or custom codecs.
These measured allocation/preparation changes do not require a new macro or a
Scala upgrade. JDK upgrades need a separate controlled experiment; no JDK
performance claim is made here.

## Benchmark harness

The pinned sbt-jmh 0.4.7 plugin supplies a consistent JMH 1.37 runtime and
generators. Redundant manual JMH overrides and unused `sbt-jmh-extras` were
removed from the benchmark project. The same build fix is applied to the
baseline; production dependencies are unchanged.

There are 27 benchmark methods and 79 parameterized cases. Every fixture checks
its values in trial setup, and each benchmark returns its result to JMH. The
baseline and first exploratory candidate measured all 79 cases. After addressing
the review, `candidate-reviewed.json` measures all 44 retained-scope cases in one
run. It excludes the 35 collection-decoder cases whose proposed changes were
reverted. Unchanged List, Vector and map encoders, prepared and fixed-schema
references, and binary encode/decode checks are included. Historical candidate
runs remain separate evidence of the rejected experiments.

Reported runs use JMH average time in ns/op, one thread, two forks, three 500 ms
warmups and five 500 ms measurements, with a 512 MiB heap and `-prof gc`. These
CLI flags override the longer annotation defaults. Compare normalized allocation
in B/op, not allocation rate in MB/s. Treat overlapping timing intervals as
inconclusive. Avoid concurrent builds or benchmarks during measurement.

## Reproduce

Run from the candidate repository root. Export the original production code and
copy the shared tests, benchmarks and benchmark build fix into the baseline:

```sh
task_repo="$PWD"
baseline_dir="$(mktemp -d "${TMPDIR:-/tmp}/avro4s-codec-baseline.XXXXXX")"
git archive e4e7a78a | tar -x -C "$baseline_dir"
cp -R "$task_repo/avro4s-core/src/test/scala/com/sksamuel/avro4s/codecs" \
  "$baseline_dir/avro4s-core/src/test/scala/com/sksamuel/avro4s/"
cp -R "$task_repo/benchmarks/src/main/scala/benchmarks/performance" \
  "$baseline_dir/benchmarks/src/main/scala/benchmarks/"
cp "$task_repo/build.sbt" "$baseline_dir/build.sbt"
cp "$task_repo/project/Build.scala" "$baseline_dir/project/Build.scala"
cd "$baseline_dir"
nix develop --command sbt 'avro4s-core/testOnly com.sksamuel.avro4s.codecs.*'
```

The original 25-test suite had five expected baseline failures. The expanded
31-test suite has nine: the original five plus four caching assertions after
serialization/restoration or a successful preparation retry. This was observed
as 22 passing and nine failing tests on the original production source. Inspect
the failure details before continuing. Compilation errors or other failures are not expected. Compile and
smoke-test the fixtures, then measure the baseline:

```sh
nix develop --command sbt 'benchmarks/Jmh/compile'
nix develop --command sbt 'benchmarks/Jmh/run -wi 1 -i 1 -w 100ms -r 100ms -f 1 -foe true benchmarks.performance.*'
mkdir -p benchmarks/results
nix develop --command sbt 'benchmarks/Jmh/run -wi 3 -w 500ms -i 5 -r 500ms -f 2 -prof gc -foe true -rf json -rff results/baseline.json benchmarks.performance.*'
```

JMH resolves relative output paths from the `benchmarks` subproject, so use
`results/...` or an absolute path. Preserve the baseline JSON without overwriting
the recorded evidence. Return to the candidate, run the full suite, compile and
measure the retained scope:

```sh
cd "$task_repo"
nix develop --command sbt test 'benchmarks/Jmh/compile'
nix develop --command sbt 'benchmarks/Jmh/run -wi 3 -w 500ms -i 5 -r 500ms -f 2 -prof gc -foe true -rf json -rff results/reproduced-candidate.json -e .*CollectionCodecBenchmarks.*To.* benchmarks.performance.*'
```

Use the same machine and exact JDK build for both revisions. The recorded runs
use Nix-store Zulu JDK 21.0.11+10; the reviewer reported that this flake's
`nix develop` supplied Zulu JDK 21.0.8. Check `java -version` in the environment
used for each run. A new comparison on 21.0.8 must rerun both baseline and
candidate; do not compare its candidate directly with the recorded 21.0.11 data. This session used the installed
Nix JDK/sbt packages and cached dependencies because `nix develop` could not
access the sandboxed Nix cache/daemon. The exact successful invocation is recorded
in [environment.md](results/environment.md); no permissions or host dependencies
were changed.


## Review follow-up

The changes in `LOCAL_REVIEW_FEEDBACK.md` were addressed as follows:

| Suggestion | Resolution |
| --- | --- |
| Magnolia array overload | Use `ctx.rawConstruct(values)`. The base implementation wraps an ArraySeq, but the derived ProductCaseClass overrides it and avoids the Seq overload's array copy. The redundant import is removed; the fresh benchmarks show lower allocation. |
| Map builder size hint | Remove it. The actual resolved Scala library is 2.13.16; its MapBuilderImpl and HashMapBuilder delegate to the empty Builder.sizeHint implementation. |
| Transient wrapper caches | Added, with four serialization tests and two preparation-retry tests. No exact serialized-form compatibility claim is made. |
| Empty-array branch | Retained for its measured allocation benefit; a comment explains the empty array/wrapper allocations it avoids. |
| Correctness test location | Renamed the package/directory to `codecs`. Benchmark classes remain under `benchmarks.performance`. The existing SchemaEvolutionTest is commented out; the added tests provide live alternating-schema/default coverage. |
| Primitive record snapshot test | Renamed to describe eager primitive reads. It is not cited as proof of array ownership; that rests on source ownership review and independent encoded-output tests. |
| Exploratory results size | All four historical JSON runs are losslessly gzip-compressed, preserving all measurements. Baseline and current-candidate JSON remain uncompressed. |
| JDK reproduction mismatch | Documented above, with the exact measured JDK retained for the review follow-up. |
| Pre-existing unreachable branches | Left unchanged, as the review explicitly places them outside this patch. |

Read compressed historical JSON with, for example:

```sh
gzip -dc benchmarks/results/candidate-initial.json.gz > /tmp/avro4s-candidate-initial.json
```

The earlier source manifest is retained as `results/candidate-before-review.sha256`.
It describes the pre-review snapshot, including the old test paths, and must not
be used to check the revised working tree. `results/candidate.sha256` describes
the revised candidate. `results/candidate-reviewed-measured.sha256` identifies
the exact benchmark source snapshot; only the decoder comment was clarified
afterward, with unchanged executable bytecode verified after recompilation.
The [comment patch](results/candidate-reviewed-comment.patch) records that exact
measured-to-final edit; reversing it reconstructs the measured source hash.

Round two approved the implementation with no blockers. Its optional provenance
and evidence-size suggestions are now addressed above. The Scala tuple ownership
path was verified in the resolved runtime bytecode, and the comparison report
now emphasizes the allocation reduction for `decodeWide`, whose timing interval
is broad. No production, build, test or benchmark sources changed in this second
follow-up; the existing 462 passing tests and 44-case measurement still apply.
