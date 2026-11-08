# Dependency update and Java support

This update keeps Java 21 and Java 25 supported. Java 21 remains the default Nix
shell and the publication baseline. Both Java and Scala compilation use a Java
21 release target, including its JDK API restrictions, when building on Java 25.

## Direct dependency changes

| Dependency | Before | Candidate |
| --- | --- | --- |
| Scala 3 LTS | 3.3.7 | 3.3.9 |
| Apache Avro | 1.11.5 | 1.12.2 |
| Kafka clients | 2.4.0 | 4.3.2 |
| Magnolia | 1.3.20 | 1.3.24 |
| json4s native | 4.0.6 | 4.0.7 |
| Cats | 2.10.0 | 2.13.0 |
| ScalaTest | 3.2.17 | 3.2.20 |
| SLF4J test libraries | 2.0.18 | 2.0.20 |
| sbt | 1.10.7 | 1.13.1 |
| sbt-pgp | 2.2.1 | 2.3.2 |
| sbt-sonatype | 3.11.0 | 3.11.0 (retained; audited) |
| sbt-jmh | 0.4.7 | 0.4.8 |

The first full OSV scan also found vulnerable transitive dependencies. These
require explicit security updates even with the direct versions above:

| Transitive dependency | Audited candidate before remediation | Selected fix |
| --- | --- | --- |
| Jackson core / databind | 2.21.2, 2.22.1 | 2.22.4 |
| Logback core | 1.3.14 | 1.5.34 |
| Guava | 16.0.1 | 33.8.0-jre |
| OkHttp / Okio | 3.14.0 / 1.17.2 | 4.12.0 / 3.6.0 |
| Apache HttpClient | 4.3.5 | 4.5.14 |
| Bouncy Castle | 1.78.1 | 1.86 |
| MessagePack | 0.9.8 | 0.9.12 |
| JLine | 3.27.1, 3.29.0 | 3.30.15 |
| jsoup | 1.18.1, 1.22.2 | 1.23.2 embedded; 1.24.1 external |
| Log4j API / core / SLF4J bridge in sbt | 2.25.4 | 2.26.1 |
| sbt Coursier integration | 2.1.13 | 2.1.14 (embeds patched jsoup) |

Jackson requirements are also direct published dependencies, so the generated
POM carries the patched versions to consumers. Compiler/documentation-tool and
publishing-plugin overrides stay in their respective build configurations.

The old external sbt-dependency-graph plugin is removed: sbt already supplies
`dependencyTree`; additional graph commands require `addDependencyTreePlugin`. Scala stays on the existing 3.3 LTS line, and json4s
stays on its stable 4.0 line rather than selecting a milestone release.
The sbt-sonatype 3.12.2 upgrade conflicts with sbtix 1.2.8's uPickle major
version during lock generation. Keep the existing publisher and scan its full
resolved graph; do not suppress the binary-incompatibility error or change the
publishing implementation without a separate compatibility check.

Security release references:

- [Avro 1.12.2](https://avro.apache.org/blog/2026/08/12/avro-1.12.2/)
- [Kafka security advisories](https://kafka.apache.org/cve-list/)
- [sbt 1.13.1](https://github.com/sbt/sbt/releases/tag/v1.13.1)
- [Scala JDK compatibility](https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html)

## Compatibility considerations

Avro 1.12.2 tightens class-instantiation restrictions. Applications using Avro
specific/reflect readers with schema class properties may need explicit trusted
class/package configuration. This library does not disable those protections or
set a wildcard trust list.

Avro 1.12 stopped rejecting some conflicting named definitions when serializing
schema JSON. `AvroSchema[T]` now validates the complete graph once, including
union, tuple, collection and custom-schema roots. Data-container output validates
before writing its header, including the explicit-schema constructor. Conflicting
definitions are rejected; equal definitions and recursive references are accepted.
Equivalence deliberately follows Avro's `Schema.equals`, as Avro 1.11 did, so
metadata such as documentation and aliases is not compared independently.

This is an explicit tightening of **schema export**, not a renaming policy.
`AvroSchema[T]` can now reject a graph that used to fail only at `toString`.
Use it when handing a schema to a registry or another process. Raw
`SchemaFor[T].schema`, binary/JSON streams and binary/JSON `GenericSerde` retain
the in-memory schema behavior: heterogeneous tuples and same-named fixed fields
can still round trip without serializing their schema. They must use the same
in-memory writer/reader graph; serializing that conflicting graph as schema JSON
is unsafe. Tuple and fixed type names are unchanged. No nested record is validated
during composition, avoiding repeated subtree checks.

Regression tests cover conflicting sealed-trait/tuple/collection roots, equal
named definitions, recursive references, binary tuple/fixed round trips, binary
Kafka serde and rejection before a data-container header is written.

The Kafka dependency change is confined to `avro4s-kafka`, but is a major client
upgrade. Applications should validate their broker/client compatibility and any
explicit Kafka dependency overrides before adopting it. The module's tests
exercise its serializer/deserializer integration; they do not simulate every
broker version or authentication configuration.

The historical JMH measurements identify the former dependency graph. They are
retained as historical evidence; this dependency update makes no new performance
claim. The next controlled experiments are in [PENDING_WORK.md](PENDING_WORK.md).

## Validation

The Java 21/25 matrix runs the Scala tests, audit-inventory/bootstrap tests,
JMH smoke cases and Java 21 classfile checks. The [PR checks](https://github.com/natural-transformation/avro4s/pull/17/checks)
retain the exact inventories, scan output, classpath provenance and JUnit reports.
A separate comparison workflow measures schema derivation against the test-only
baseline `0df287d8` on each JDK; timings on shared runners are evidence for this
fixture, not a pass/fail performance threshold or a Java 21-versus-25 comparison.

Java 21 additionally builds offline and exercises `publishSigned` against an
isolated local Maven repository with an ephemeral GPG key. All library JARs,
sources, documentation and POMs must exist and their signatures must verify.
This does not exercise Central's authenticated remote upload or production keys.
Release jobs resolve the requested branch once and pass the same commit SHA to
both validation and publication. A known advisory, including in build/test tools,
blocks release; there is no automatic exception or severity threshold.

From a fresh checkout, run each JDK separately with a fresh sbt boot directory:

```sh
SBT_BOOT="$(mktemp -d)" nix develop .#java21 --command bash -c \
  'bash scripts/ci-validate.sh && bash scripts/dependency-audit.sh'
SBT_BOOT="$(mktemp -d)" nix develop .#java25 --command bash -c \
  'bash scripts/ci-validate.sh && bash scripts/dependency-audit.sh'
nix build
```

The CI matrix runs the full core/Cats/Kafka test suites, compiles JMH and runs
fixture smoke checks on each JDK. It also checks that every compiled class in
published modules is Java 21 compatible. Smoke timings are not performance
evidence. PR, master and release workflows share this validation; publishing
depends on both Java jobs succeeding.

## Vulnerability checks

`scripts/dependency_inventory.py` exports selected, non-evicted dependencies from
sbt's resolved reports for core, Cats, Kafka, benchmarks and the build plugins.
This includes runtime, test and transitive dependencies. It additionally inspects
the actual sbt bootstrap JARs and launcher, including embedded Maven metadata, so the sbt
runtime is not represented only by its top-level version number. Missing reports,
unresolved artifacts or unidentifiable bootstrap JARs fail the check.

The Nix-provided OSV-Scanner checks that inventory against its live advisory
database on both JDKs. Any finding, at any severity, or scan error fails CI. There
are no advisory suppressions. Inventories, scan results and test reports are
uploaded as `java-21-validation` and `java-25-validation` artifacts on each run.

A successful scan means no known matching advisories for the resolved Maven
coordinates in OSV at scan time. It cannot guarantee the absence of undiscovered
vulnerabilities and is not an audit of every native package in the Nix closure.
The JDK and tooling closure is pinned through `flake.lock` and must be refreshed
alongside upstream security releases.

The Java 21 package in that nixpkgs snapshot predates the October security
release. `nix/jdk21.nix` uses its existing Temurin packaging with the official
[21.0.13+9 release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9)
assets and SHA-256 hashes for all three supported platforms. Java 25 uses the
pinned nixpkgs JDK (OpenJDK on Linux, Zulu on aarch64-darwin). The actual versions are recorded in CI artifacts.

`nix/sbt.nix` constructs sbt's real startup classpath from the resolved bootstrap
lock in `nix/sbt-bootstrap.json`. It checks the locked sbt version against
`project/build.properties`. A process lock and atomic directory rename initialize
a writable cache once; subsequent launches do not overwrite live JARs. Interrupted
initialization is recoverable. Old cache generations are retained because another
JVM may still be using them; remove unused generations only after stopping sbt and
IDE sessions. The wrapper overrides conflicting `SBT_OPTS` boot-directory settings;
CI deliberately supplies one and verifies the resulting classpath. Ordinary project `dependencyOverrides` cannot change that startup classpath.
The bootstrap also updates Coursier itself: its shaded jsoup copy cannot be fixed
by adding a newer, separate jsoup JAR. The audit inventories both outer artifacts
and embedded Maven metadata. Use the Nix shell to obtain these build-tool fixes;
an independently installed sbt launcher is outside this managed environment.
The audit exports the launcher's actual classpath and fails if any of those JARs
is outside the scanned bootstrap directory or differs in content from the
pinned Nix bootstrap. It also checks the actual launcher path. Merely seeding a
clean, unused cache cannot satisfy the check. Outer JAR identification must succeed
even when embedded metadata is present. Metadata-based scanning cannot enumerate
bundled libraries whose upstream JAR strips all identifying metadata.

## Refreshing locks

After changing dependencies, use the repository's flake-native sbtix flow:

```sh
nix flake update nixpkgs
nix develop .#java21 --command bash scripts/refresh-dependency-locks.sh
nix build
```

Review and commit every changed lock imported by `default.nix`, including the
nested build-plugin locks. The dependency-lock refresh workflow produces a patch
artifact for review; it does not commit or publish changes automatically.
The refresh script resolves sbt itself in a temporary project and writes only
the selected compile JARs and their hashes into `nix/sbt-bootstrap.json`. It reads
sbtix's output using Nix evaluation, without rewriting generated Nix text. Missing
Scala bootstrap JARs are pinned explicitly. The large temporary bootstrap metadata
lock (including sources, tests and optional artifacts) is not imported by the
offline library build; that build uses the same pinned boot tree.
The shell's sbtix CLI uses the patched repository sbt/JDK, while reusing sbtix's
prebuilt plugin. The bootstrap audit covers the running sbt distribution, not the
historical toolchain used to build the sbtix plugin derivation.
When changing sbt itself, enter the existing working Nix shell before editing
`project/build.properties`, then refresh the lock from that shell before evaluating
the updated flake. The new shell rejects mismatched sbt/lock versions.
The sbtix Nix adapter maps its legacy `lib.fold` reference to `lib.foldr`, which
preserves the old alias's behavior with the current nixpkgs release.
