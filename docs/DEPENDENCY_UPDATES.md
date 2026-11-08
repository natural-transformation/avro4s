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
| Logback core / classic | 1.3.14 | 1.5.34 |
| Guava | 16.0.1 | 33.8.0-jre |
| OkHttp / Okio | 3.14.0 / 1.17.2 | 4.12.0 / 3.6.0 |
| Apache HttpClient | 4.3.5 | 4.5.14 |
| Bouncy Castle | 1.78.1 | 1.86 |
| MessagePack | 0.9.8 | 0.9.12 |
| JLine | 3.27.1, 3.29.0 | 3.30.15 |
| jsoup | 1.18.1, 1.22.2 | 1.23.2 embedded; 1.24.1 external |
| Log4j API / core in sbt | 2.25.4 | 2.26.1 |
| sbt Coursier integration | 2.1.13 | 2.1.14 (embeds patched jsoup) |

Jackson requirements are also direct published dependencies, so the generated
POM carries the patched versions to consumers. Compiler/documentation-tool and
publishing-plugin overrides stay in their respective build configurations.

The old external sbt-dependency-graph plugin is removed: sbt already supplies
dependency graph commands. Scala stays on the existing 3.3 LTS line, and json4s
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

Avro 1.12 also stopped rejecting certain conflicting named definitions during
schema serialization. The existing erased-generic-name regression failed after
the upgrade. Derived records now validate name consistency before returning a
schema, preserving rejection of incompatible same-name definitions. Tests cover
equal repeated definitions, nested array/map collisions and recursive references.

The Kafka dependency change is confined to `avro4s-kafka`, but is a major client
upgrade. Applications should validate their broker/client compatibility and any
explicit Kafka dependency overrides before adopting it. The module's tests
exercise its serializer/deserializer integration; they do not simulate every
broker version or authentication configuration.

The historical JMH measurements identify the former dependency graph. They are
retained as historical evidence; this dependency update makes no new performance
claim. The next controlled experiments are in [PENDING_WORK.md](PENDING_WORK.md).

## Validation

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
the actual sbt bootstrap JARs, including embedded Maven metadata, so the sbt
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
pinned nixpkgs OpenJDK build. The actual versions are recorded in CI artifacts.

`nix/sbt.nix` constructs sbt's real startup classpath from the resolved bootstrap
lock, then copies it into a writable, content-addressed cache before launching
sbt. Ordinary project `dependencyOverrides` cannot change that startup classpath.
The bootstrap also updates Coursier itself: its shaded jsoup copy cannot be fixed
by adding a newer, separate jsoup JAR. The audit inventories both outer artifacts
and embedded Maven metadata. Use the Nix shell to obtain these build-tool fixes;
an independently installed sbt launcher is outside this managed environment.
The audit exports the launcher's actual classpath and fails if any of those JARs
is outside the scanned bootstrap directory; merely seeding a clean, unused cache
cannot satisfy the check.

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
The refresh script also resolves the selected sbt runtime as an explicit
dependency into `sbt-bootstrap-repo.nix`: sbtix's bundled bootstrap lock targets
its own sbt version and does not automatically follow this project's upgrade.
`sbt-bootstrap-jars.json` records only the selected compile JARs; offline
repository metadata also contains optional/test artifacts that must not be
loaded into the running build tool.
