#!/usr/bin/env python3
"""Export resolved sbt dependencies and bootstrap JARs for OSV-Scanner.

Run after a clean build, including benchmarks/update. Missing reports or JAR
coordinates are errors: a partial inventory must never look like a clean scan.
"""
import argparse
import json
from pathlib import Path
import re
import zipfile

PROJECTS = ("project", "avro4s-core", "avro4s-cats", "avro4s-kafka", "benchmarks")
# These bootstrap libraries omit Maven properties and a Maven group in their
# manifest. Names are exact; an unknown artifact is an error, never skipped.
BOOT_GROUPS = {
    "jna": "net.java.dev.jna", "jna-platform": "net.java.dev.jna",
    "checker-qual": "org.checkerframework", "caffeine": "com.github.ben-manes.caffeine",
    "reactive-streams": "org.reactivestreams", "disruptor": "com.lmax",
    "scala-library": "org.scala-lang", "scala-reflect": "org.scala-lang",
}


def resolved_modules(report):
    found = set()
    for configuration in report["configurations"]:
        for entry in configuration["modules"]:
            if entry["evicted"]:
                continue
            if entry["missingArtifacts"]:
                raise ValueError("Unresolved dependency: " + str(entry["module"]))
            module = entry["module"]
            name = module["name"]
            attrs = module.get("extraAttributes", {})
            # sbt reports plugin names without their Maven cross-version suffix.
            if "sbtVersion" in attrs and "scalaVersion" in attrs:
                suffix = "_" + attrs["scalaVersion"] + "_" + attrs["sbtVersion"]
                if not name.endswith(suffix):
                    name += suffix
            if entry["artifacts"]:
                found.add((module["organization"] + ":" + name, module["revision"]))
    if not found:
        raise ValueError("Empty resolved dependency report")
    return found


def jar_modules(path):
    with zipfile.ZipFile(path) as jar:
        properties = [name for name in jar.namelist()
                      if name.startswith("META-INF/maven/") and name.endswith("/pom.properties")]
        modules = set()
        for name in properties:
            values = dict(line.split("=", 1) for line in jar.read(name).decode().splitlines()
                          if "=" in line and not line.startswith(("#", "!")))
            modules.add((values["groupId"] + ":" + values["artifactId"], values["version"]))
        if modules and "META-INF/MANIFEST.MF" not in jar.namelist():
            return modules
        text = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n", "\n").replace("\n ", "")
        attrs = dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)
    version = attrs.get("Implementation-Version", attrs.get("Specification-Version", ""))
    if path.stem.startswith(("scala-library", "scala-reflect")):
        version = attrs.get("Bundle-Version", version).split(".v")[0]
    if path.stem.startswith(("jna-", "jna-platform-")):
        version = attrs.get("Bundle-Version", version)
    if not version:
        version = attrs.get("Bundle-Version", "")
    artifact = path.stem.removesuffix("-" + version)
    if artifact.startswith("org.scala-sbt-compiler-bridge_"):
        artifact = artifact.removeprefix("org.scala-sbt-").split("-" + version + "-bin_")[0]
    group = attrs.get("Implementation-Vendor-Id", attrs.get("Specification-Vendor", ""))
    group = BOOT_GROUPS.get(artifact, group)
    if (not re.fullmatch(r"[\w-]+(?:\.[\w-]+)+", group) or not version
            or artifact == path.stem and re.search(r"-\d", artifact)):
        if modules:
            return modules
        raise ValueError("Cannot identify bootstrap JAR: " + str(path))
    return modules | {(group + ":" + artifact, version)}


def check_bootstrap_classpath(boot, classpath):
    paths = [Path(line).resolve() for line in classpath.read_text().splitlines() if line]
    if not paths:
        raise ValueError("Empty actual sbt bootstrap classpath")
    for path in paths:
        if not path.is_file() or not path.is_relative_to(boot.resolve()):
            raise ValueError("sbt used a bootstrap JAR outside the audited directory: " + str(path))


def collect(root, boot):
    sources = []
    for project in PROJECTS:
        reports = sorted((root / project / "target").glob("**/update/**/output"))
        if not reports:
            raise ValueError("Missing resolved report for " + project)
        modules = set()
        for report in reports:
            modules.update(resolved_modules(json.loads(report.read_text())))
        sources.append((project, modules))
    jars = sorted(boot.rglob("*.jar"))
    if not jars:
        raise ValueError("No sbt bootstrap JARs in " + str(boot))
    modules = set()
    for jar in jars:
        modules.update(jar_modules(jar))
    sources.append(("sbt-bootstrap", modules))
    return {"results": [
        {"source": {"path": source, "type": "sbt"},
         "packages": [{"package": {"ecosystem": "Maven", "name": name, "version": version}}
                      for name, version in sorted(modules)]}
        for source, modules in sources
    ]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--boot", type=Path, required=True)
    parser.add_argument("--classpath", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    check_bootstrap_classpath(args.boot, args.classpath)
    inventory = collect(args.root, args.boot)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(inventory, indent=2) + "\n")
    packages = {(p["package"]["name"], p["package"]["version"])
                for source in inventory["results"] for p in source["packages"]}
    print("Exported", len(packages), "unique Maven coordinates across", len(inventory["results"]), "sources")


if __name__ == "__main__":
    main()
