#!/usr/bin/env python3
"""Publish a writable bootstrap copy atomically, without rewriting live JARs."""
import fcntl
from pathlib import Path
import shutil
import sys
import tempfile


def prepare(source, root):
    source, root = Path(source).resolve(), Path(root).resolve()
    root.mkdir(parents=True, exist_ok=True)
    destination = root / (source.name + "-v2")
    # Keep the lock inode: unlinking it would let a third process bypass waiters.
    with (root / (destination.name + ".lock")).open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if not (destination / ".complete").is_file():
            # Only incomplete v2 caches can be removed: no launcher is handed
            # this path until publication. Older cache generations are untouched.
            if destination.exists():
                for directory in [destination, *destination.rglob("*")]:
                    if directory.is_dir():
                        directory.chmod(directory.stat().st_mode | 0o700)
                shutil.rmtree(destination)
            with tempfile.TemporaryDirectory(prefix=destination.name + ".tmp-", dir=root) as stage:
                tree = Path(stage) / "boot"
                shutil.copytree(source, tree, symlinks=False)
                for path in [tree, *tree.rglob("*")]:
                    path.chmod(path.stat().st_mode | 0o200)
                (tree / ".complete").touch()
                tree.rename(destination)
    return destination


if __name__ == "__main__":
    print(prepare(*sys.argv[1:]))
