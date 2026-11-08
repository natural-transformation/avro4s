#!/usr/bin/env python3
"""Reject published-module classes requiring a JVM newer than Java 21."""
from pathlib import Path
import struct

total = 0
for module in ("avro4s-core", "avro4s-cats", "avro4s-kafka"):
    classes = list(Path(module, "target").glob("scala-*/classes/**/*.class"))
    if not classes:
        raise SystemExit("No compiled classes found for " + module)
    for path in classes:
        with path.open("rb") as stream:
            magic, minor, major = struct.unpack(">IHH", stream.read(8))
        if magic != 0xCAFEBABE or major > 65 or minor == 65535:
            raise SystemExit("Not Java 21 compatible: " + str(path))
    total += len(classes)
print("Verified Java 21 bytecode compatibility for", total, "published-module classes")
