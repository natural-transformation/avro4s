#!/usr/bin/env python3
"""Write a selected bootstrap lock without modifying sbtix's generated Nix."""
import argparse
import json
from pathlib import Path
import subprocess

from dependency_inventory import resolved_jar_urls


def selected_lock(repo, reports, fetch_hash):
    versioning = repo["versioning"]
    if len(versioning) != 1:
        raise ValueError("Expected exactly one sbt/Scala bootstrap version")
    versions = versioning[0]
    scala = versions["scalaVersion"]
    if not reports:
        raise ValueError("Missing bootstrap resolution report")
    urls = set().union(*(resolved_jar_urls(report) for report in reports))
    for module in ("scala-library", "scala-reflect", "scala-compiler"):
        urls.add(f"https://repo1.maven.org/maven2/org/scala-lang/{module}/{scala}/{module}-{scala}.jar")
    hashes = {artifact["url"]: artifact["sha256"] for artifact in repo["artifacts"].values()}
    artifacts = [{"url": url, "sha256": hashes[url] if url in hashes else fetch_hash(url)}
                 for url in sorted(urls)]
    return {"sbtVersion": versions["sbtVersion"], "scalaVersion": scala, "artifacts": artifacts}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    repo = json.loads(subprocess.check_output(
        ["nix-instantiate", "--eval", "--strict", "--json", str(args.directory / "repo.nix")], text=True))
    reports = [json.loads(path.read_text()) for path in (args.directory / "target").glob("**/update/**/output")]
    def fetch_hash(url):
        return subprocess.check_output(["nix-prefetch-url", "--type", "sha256", url], text=True).strip()
    lock = selected_lock(repo, reports, fetch_hash)
    args.output.write_text(json.dumps(lock, indent=2) + "\n")


if __name__ == "__main__":
    main()
