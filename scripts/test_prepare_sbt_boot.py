from concurrent.futures import ProcessPoolExecutor
from pathlib import Path
import tempfile
import unittest

from prepare_sbt_boot import prepare


class PrepareBootTest(unittest.TestCase):
    def test_concurrent_initialization_and_relaunch_do_not_rewrite_jars(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "immutable-boot"
            source.mkdir()
            jar = source / "library.jar"
            jar.write_bytes(b"pinned library")
            jar.chmod(0o444)
            cache = root / "cache with spaces"
            with ProcessPoolExecutor(4) as pool:
                results = list(pool.map(prepare, [source] * 8, [cache] * 8))
            self.assertEqual(len(set(results)), 1)
            copied = results[0] / "library.jar"
            before = copied.stat()
            prepare(source, cache)
            after = copied.stat()
            self.assertEqual((before.st_ino, before.st_mtime_ns), (after.st_ino, after.st_mtime_ns))
            self.assertEqual(copied.read_bytes(), b"pinned library")
            self.assertTrue(after.st_mode & 0o200)

    def test_incomplete_read_only_cache_is_recovered(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "immutable-boot"
            source.mkdir()
            (source / "library.jar").write_bytes(b"complete")
            cache = root / "cache"
            partial = cache / (source.name + "-v2")
            partial.mkdir(parents=True)
            (partial / "library.jar").write_bytes(b"partial")
            (partial / "library.jar").chmod(0o444)
            self.assertEqual((prepare(source, cache) / "library.jar").read_bytes(), b"complete")


if __name__ == "__main__":
    unittest.main()
