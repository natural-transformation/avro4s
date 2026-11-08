import unittest
from lock_sbt_bootstrap import selected_lock


class BootstrapLockTest(unittest.TestCase):
    def test_selected_classpath_uses_existing_hashes_and_pins_missing_scala_jars(self):
        url = "https://repo1.maven.org/maven2/example/library/1.0/library-1.0.jar"
        repo = {"versioning": [{"scalaVersion": "2.12.21", "sbtVersion": "1.13.1"}],
                "artifacts": {"unimportant-format": {"url": url, "sha256": "existing"}}}
        report = {"configurations": [{"configuration": {"name": "compile"}, "modules": [
            {"module": {"organization": "example", "name": "library", "revision": "1.0"},
             "evicted": False, "missingArtifacts": [],
             "artifacts": [[{"name": "library", "extension": "jar"}, "file:///library.jar"]]}]}]}
        fetched = []
        def fetch(url):
            fetched.append(url)
            return "new"
        result = selected_lock(repo, [report], fetch)
        self.assertEqual(result["artifacts"][0], {"url": url, "sha256": "existing"})
        self.assertEqual(len(fetched), 3)
        self.assertEqual(len(result["artifacts"]), 4)
        with self.assertRaises(ValueError):
            selected_lock(repo, [], fetch)


if __name__ == "__main__":
    unittest.main()
