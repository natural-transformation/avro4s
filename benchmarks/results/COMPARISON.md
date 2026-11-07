# Measured codec comparison

Measured on 7 November 2026 with the same installed Nix JDK 21.0.11,
Scala 3.3.7 and JMH 1.37. The baseline is production commit `e4e7a78a`;
[the source hashes](candidate.sha256) identify the final candidate;
[the measured snapshot](candidate-reviewed-measured.sha256) differs only by a
[post-run decoder-comment correction](candidate-reviewed-comment.patch), with
identical executable bytecode verified. The patch runs from measured to final;
reversing its one-line edit reconstructs the measured source hash.
[Environment and commands](environment.md) and [reproduction instructions](../PERFORMANCE.md)
are included. Raw data: [baseline](baseline.json) and
[reviewed candidate](candidate-reviewed.json).

All 462 candidate tests pass, including 31 codec regression tests. Six tests were
added during review for serialization and preparation retry. Before the transient
cache change, the two non-serializable-prepared-state tests failed; both now pass.
The original 25-test suite passed 20 tests on the production baseline and failed
exactly five. Rerunning the expanded 31-test suite on that original production
source gives 22 passes and nine expected failures: those five plus four caching
assertions after restoration or successful preparation retry.

All 44 candidate rows below come from one fresh run after the review changes,
with two JVM forks per case. Historical measurements remain separate; they are
not substituted for the revised source. The benchmark fixtures and build
configuration are unchanged from the original baseline run.

## Representative results

Lower time and allocation are better. Ratio is baseline time / final time;
values above 1 indicate a lower final mean. `±` is JMH's reported score error.
`faster`/`slower` means the two reported timing confidence intervals do not
overlap; `overlap` is inconclusive. These labels are descriptive, not a
multiple-comparison-corrected statistical test. Bytes/op are rounded to the
nearest byte; tiny nonzero profiler overhead is not a useful allocation claim.

| Benchmark | Size | Baseline ns/op | Final ns/op | Ratio | Baseline B/op | Final B/op | Timing intervals |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `CodecPreparationBenchmarks.wrapperEncode` | — | 1742.31 ± 18.80 | 144.94 ± 4.39 | 12.02× | 12496 | 1152 | faster |
| `CodecPreparationBenchmarks.wrapperDecode` | — | 998.86 ± 48.15 | 164.55 ± 1.48 | 6.07× | 5416 | 944 | faster |
| `RecordCodecBenchmarks.encodeFlat` | — | 23.38 ± 0.52 | 20.81 ± 0.24 | 1.12× | 232 | 200 | faster |
| `RecordCodecBenchmarks.decodeWide` | — | 81.97 ± 3.55 | 49.30 ± 11.77 | 1.66× | 672 | 528 | faster |
| `CollectionCodecBenchmarks.arrayEncode` | 128 | 527.24 ± 3.46 | 318.34 ± 2.81 | 1.66× | 7736 | 4664 | faster |
| `CollectionCodecBenchmarks.arrayEncode` | 1024 | 4051.05 ± 34.62 | 2355.25 ± 8.86 | 1.72× | 61496 | 36920 | faster |
| `MapCodecBenchmarks.stringKeyMapDecode` | 128 | 8848.58 ± 142.07 | 4054.18 ± 32.32 | 2.18× | 40520 | 20872 | faster |
| `MapCodecBenchmarks.utf8KeyMapDecode` | 1024 | 94094.14 ± 4855.66 | 43627.85 ± 3172.66 | 2.16× | 424521 | 217585 | faster |
| `RecordBinaryBenchmarks.encodeBinary` | — | 333.92 ± 1.86 | 318.57 ± 2.69 | 1.05× | 1384 | 1224 | faster |
| `RecordBinaryBenchmarks.decodeBinary` | — | 427.99 ± 26.05 | 342.31 ± 4.68 | 1.25× | 2104 | 1720 | faster |

## Interpretation and limits

- Wrapper benchmarks reuse one wrapper across records. The caching result applies
  to that usage; constructing a wrapper for every record cannot amortize its
  preparation. Already-prepared reference methods below help separate preparation
  overhead from the smaller record-storage changes. Preparation-count tests
  independently establish the once-per-wrapper behavior.
- Record changes remove redundant field-array copies. The direct Magnolia
  ProductCaseClass array overload also skips its Seq overload's `toArray` copy:
  flat decoding now allocates 168 B/op, versus 216 B/op before review.
  Allocation is the main
  evidence where timing intervals overlap. The schema-identity correctness fix
  is measured together with decoder storage changes, not claimed as a speedup.
- Wide-record decoding has a broad timing interval: 49.30 ± 11.77 ns/op versus
  81.97 ± 3.55 in the baseline. The intervals do not overlap, but the 1.66× ratio
  is a point estimate; the allocation reduction from 672 to 528 B/op is the
  steadier evidence for this case.
- Array encoding preserves Scala's specialized `Array.map` and removes the
  subsequent List. The fixtures use `Array[Int]`; no universal claim is made for
  every primitive/reference element type or custom codec.
- Map decoding avoids constructing an intermediate immutable map. The string and
  Utf8 cases exercise the standard Avro key representations. Map encoding was
  reverted entirely; its final measurements are unchanged controls.
- Binary benchmarks include Avro binary writing/reading around a nested record.
  They reuse the binary codecs and are microbenchmarks, not file/network or
  application throughput measurements.
- List, Vector and map encoding and fixed-schema direct references are unchanged
  controls. Differences in those rows illustrate fork/JIT/environment variation
  and must not be attributed to a source optimization in those paths.
- This is one arm64 machine, one JDK and one sequence of runs, with 3 × 500 ms
  warmup and 5 × 500 ms measurement per fork. Small timing differences deserve
  caution. No JDK-upgrade or general macro performance claim is established.

## Complete final matrix

The final matrix contains all changed operations and controls. The 35 excluded
collection-decoder cases were reverted to baseline production code; their
exploratory measurements remain available below.

| Benchmark | Size | Baseline ns/op | Final ns/op | Ratio | Baseline B/op | Final B/op | Timing intervals |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `CodecPreparationBenchmarks.preparedDecodeReference` | — | 208.39 ± 1.74 | 164.14 ± 1.44 | 1.27× | 1328 | 944 | faster |
| `CodecPreparationBenchmarks.preparedEncodeReference` | — | 151.71 ± 1.93 | 146.03 ± 0.81 | 1.04× | 1312 | 1152 | faster |
| `CodecPreparationBenchmarks.wrapperDecode` | — | 998.86 ± 48.15 | 164.55 ± 1.48 | 6.07× | 5416 | 944 | faster |
| `CodecPreparationBenchmarks.wrapperEncode` | — | 1742.31 ± 18.80 | 144.94 ± 4.39 | 12.02× | 12496 | 1152 | faster |
| `CollectionCodecBenchmarks.arrayEncode` | 0 | 4.88 ± 0.06 | 3.92 ± 0.05 | 1.24× | 40 | 24 | faster |
| `CollectionCodecBenchmarks.arrayEncode` | 1 | 8.51 ± 0.19 | 8.09 ± 0.13 | 1.05× | 104 | 96 | faster |
| `CollectionCodecBenchmarks.arrayEncode` | 8 | 35.51 ± 1.03 | 23.72 ± 0.78 | 1.50× | 520 | 344 | faster |
| `CollectionCodecBenchmarks.arrayEncode` | 128 | 527.24 ± 3.46 | 318.34 ± 2.81 | 1.66× | 7736 | 4664 | faster |
| `CollectionCodecBenchmarks.arrayEncode` | 1024 | 4051.05 ± 34.62 | 2355.25 ± 8.86 | 1.72× | 61496 | 36920 | faster |
| `CollectionCodecBenchmarks.listEncode` | 0 | 4.10 ± 0.03 | 3.92 ± 0.05 | 1.04× | 24 | 24 | faster |
| `CollectionCodecBenchmarks.listEncode` | 1 | 4.91 ± 0.10 | 4.89 ± 0.11 | 1.00× | 64 | 64 | overlap |
| `CollectionCodecBenchmarks.listEncode` | 8 | 30.43 ± 0.45 | 30.31 ± 0.31 | 1.00× | 344 | 344 | overlap |
| `CollectionCodecBenchmarks.listEncode` | 128 | 501.00 ± 8.41 | 497.61 ± 9.84 | 1.01× | 5144 | 5144 | overlap |
| `CollectionCodecBenchmarks.listEncode` | 1024 | 4079.82 ± 346.31 | 4056.16 ± 356.14 | 1.01× | 40984 | 40984 | overlap |
| `CollectionCodecBenchmarks.vectorEncode` | 0 | 4.11 ± 0.04 | 3.99 ± 0.06 | 1.03× | 24 | 24 | faster |
| `CollectionCodecBenchmarks.vectorEncode` | 1 | 6.35 ± 0.05 | 6.35 ± 0.09 | 1.00× | 88 | 88 | overlap |
| `CollectionCodecBenchmarks.vectorEncode` | 8 | 27.16 ± 0.46 | 27.05 ± 0.31 | 1.00× | 392 | 392 | overlap |
| `CollectionCodecBenchmarks.vectorEncode` | 128 | 490.44 ± 6.59 | 482.90 ± 2.01 | 1.02× | 5856 | 5856 | overlap |
| `CollectionCodecBenchmarks.vectorEncode` | 1024 | 4208.55 ± 74.46 | 4144.70 ± 102.69 | 1.02× | 45840 | 45840 | overlap |
| `MapCodecBenchmarks.mapEncode` | 0 | 1.79 ± 0.02 | 1.78 ± 0.02 | 1.01× | 48 | 48 | overlap |
| `MapCodecBenchmarks.mapEncode` | 1 | 10.09 ± 1.04 | 10.11 ± 0.84 | 1.00× | 188 | 188 | overlap |
| `MapCodecBenchmarks.mapEncode` | 8 | 54.47 ± 8.27 | 49.08 ± 0.34 | 1.11× | 512 | 512 | overlap |
| `MapCodecBenchmarks.mapEncode` | 128 | 1265.80 ± 121.91 | 1234.74 ± 43.88 | 1.03× | 8280 | 8280 | overlap |
| `MapCodecBenchmarks.mapEncode` | 1024 | 14016.35 ± 246.34 | 13992.44 ± 371.30 | 1.00× | 90248 | 90248 | overlap |
| `MapCodecBenchmarks.stringKeyMapDecode` | 0 | 6.29 ± 0.13 | 1.09 ± 0.01 | 5.75× | 72 | 0 | faster |
| `MapCodecBenchmarks.stringKeyMapDecode` | 1 | 21.70 ± 0.76 | 9.51 ± 0.06 | 2.28× | 96 | 40 | faster |
| `MapCodecBenchmarks.stringKeyMapDecode` | 8 | 543.52 ± 169.52 | 267.83 ± 2.73 | 2.03× | 2336 | 1112 | faster |
| `MapCodecBenchmarks.stringKeyMapDecode` | 128 | 8848.58 ± 142.07 | 4054.18 ± 32.32 | 2.18× | 40520 | 20872 | faster |
| `MapCodecBenchmarks.stringKeyMapDecode` | 1024 | 87924.96 ± 1759.79 | 38844.82 ± 564.50 | 2.26× | 424521 | 205297 | faster |
| `MapCodecBenchmarks.utf8KeyMapDecode` | 0 | 6.22 ± 0.03 | 1.12 ± 0.02 | 5.54× | 72 | 0 | faster |
| `MapCodecBenchmarks.utf8KeyMapDecode` | 1 | 21.17 ± 0.22 | 9.62 ± 0.18 | 2.20× | 96 | 40 | faster |
| `MapCodecBenchmarks.utf8KeyMapDecode` | 8 | 585.92 ± 184.63 | 273.33 ± 6.20 | 2.14× | 2424 | 1136 | faster |
| `MapCodecBenchmarks.utf8KeyMapDecode` | 128 | 9125.33 ± 60.43 | 4144.67 ± 51.78 | 2.20× | 37448 | 20872 | faster |
| `MapCodecBenchmarks.utf8KeyMapDecode` | 1024 | 94094.14 ± 4855.66 | 43627.85 ± 3172.66 | 2.16× | 424521 | 217585 | faster |
| `RecordBinaryBenchmarks.decodeBinary` | — | 427.99 ± 26.05 | 342.31 ± 4.68 | 1.25× | 2104 | 1720 | faster |
| `RecordBinaryBenchmarks.encodeBinary` | — | 333.92 ± 1.86 | 318.57 ± 2.69 | 1.05× | 1384 | 1224 | faster |
| `RecordCodecBenchmarks.decodeFlat` | — | 36.67 ± 0.41 | 23.11 ± 0.44 | 1.59× | 248 | 168 | faster |
| `RecordCodecBenchmarks.decodeFlatDirectReference` | — | 11.89 ± 0.11 | 11.79 ± 0.13 | 1.01× | 104 | 104 | overlap |
| `RecordCodecBenchmarks.decodeNested` | — | 218.54 ± 3.38 | 165.51 ± 3.24 | 1.32× | 1328 | 944 | faster |
| `RecordCodecBenchmarks.decodeWide` | — | 81.97 ± 3.55 | 49.30 ± 11.77 | 1.66× | 672 | 528 | faster |
| `RecordCodecBenchmarks.encodeFlat` | — | 23.38 ± 0.52 | 20.81 ± 0.24 | 1.12× | 232 | 200 | faster |
| `RecordCodecBenchmarks.encodeFlatDirectReference` | — | 11.86 ± 0.25 | 11.76 ± 0.23 | 1.01× | 176 | 176 | overlap |
| `RecordCodecBenchmarks.encodeNested` | — | 149.84 ± 0.57 | 146.53 ± 1.31 | 1.02× | 1312 | 1152 | faster |
| `RecordCodecBenchmarks.encodeWide` | — | 57.36 ± 0.63 | 57.00 ± 2.64 | 1.01× | 744 | 680 | overlap |

## Rejected or revised experiments

The exploratory runs are retained as separate measurements, not merged into the
final JSON. [Initial candidate](candidate-initial.json.gz) and
[its diff](candidate-initial.patch) cover 79 cases. The
[array-refinement run](candidate-array-refinement.json.gz) and
[its diff](candidate-array-refinement.patch) cover 44 cases after reverting the
collection decoders and restoring `Array.map`. That stage still optimized Vector
encoding and always presized maps. The next [guarded-map run](candidate-guarded-map.json.gz)
([diff](candidate-guarded-map.patch)) reverted Vector encoding and limited map
presizing to larger inputs. That map proposal was then reverted as well.

| Experiment | Case | Baseline ns/op | Experiment ns/op | Baseline B/op | Experiment B/op | Decision |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Iterator collection decoding | `CollectionCodecBenchmarks.javaCollectionToVector` (0) | 1.00 ± 0.02 | 6.73 ± 0.30 | 0 | 200 | Reverted |
| Iterator collection decoding | `CollectionCodecBenchmarks.scalaVectorToVector` (128) | 255.17 ± 4.26 | 671.64 ± 42.34 | 2680 | 2960 | Reverted |
| Iterator collection decoding | `CollectionCodecBenchmarks.scalaVectorToVector` (1024) | 2434.48 ± 40.21 | 5446.75 ± 64.20 | 21160 | 21440 | Reverted |
| Generic array loop | `CollectionCodecBenchmarks.arrayEncode` (128) | 527.24 ± 3.46 | 661.21 ± 12.57 | 7736 | 4664 | Use Array.map |
| Indexed-sequence encoding | `CollectionCodecBenchmarks.vectorEncode` (8) | 27.16 ± 0.46 | 31.49 ± 0.57 | 392 | 216 | Reverted |
| Indexed-sequence encoding | `CollectionCodecBenchmarks.vectorEncode` (128) | 490.44 ± 6.59 | 562.21 ± 3.97 | 5856 | 2696 | Reverted |
| Indexed-sequence encoding | `CollectionCodecBenchmarks.vectorEncode` (1024) | 4208.55 ± 74.46 | 3264.64 ± 9.86 | 45840 | 20640 | Large-case win did not justify mid-size regressions |
| Always-presized map | `MapCodecBenchmarks.mapEncode` (8) | 54.47 ± 8.27 | 52.01 ± 4.62 | 512 | 620 | Revised, then rejected |
| Guarded map presizing | `MapCodecBenchmarks.mapEncode` (8) | 54.47 ± 8.27 | 48.86 ± 0.67 | 512 | 572 | Reverted map encoding entirely |


The [pre-review unchanged map-encoding control](candidate-map-control.json.gz)
at eight entries also measured 572 B/op
(512 in one fork, 632 in the other), versus 512 B/op in the baseline. The fresh reviewed control is again 512 B/op. Baseline
and final MapEncoder disassemblies were identical. Those measurements do not
establish that presizing caused the small-map allocation variation. No
improvement is claimed for the unchanged map encoder.

The `*DirectReference` methods are hand-written for one fixed schema. They are
exploration of specialization potential, not a macro implementation or proof of
compatibility. General derivation changes remain outside this patch.
