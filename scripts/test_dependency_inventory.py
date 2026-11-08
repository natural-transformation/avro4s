from pathlib import Path
import tempfile
import unittest
import zipfile
from dependency_inventory import collect, jar_modules, resolved_modules


class DependencyInventoryTest(unittest.TestCase):
    def module(self, name="library", version="1.0", **kwargs):
        return {"module": {"organization": "example.org", "name": name, "revision": version},
                "evicted": False, "artifacts": [[{}, "file:///a.jar"]], "missingArtifacts": [], **kwargs}

    def report(self, *modules):
        return {"configurations": [{"modules": list(modules)}]}

    def test_evicted_versions_are_excluded_but_selected_test_dependencies_remain(self):
        report = self.report(self.module(version="0.9", evicted=True), self.module(), self.module("test-library"))
        self.assertEqual(resolved_modules(report), {("example.org:library", "1.0"), ("example.org:test-library", "1.0")})

    def test_sbt_plugin_uses_real_maven_coordinates(self):
        module = self.module("sbt-plugin")
        module["module"]["extraAttributes"] = {"sbtVersion": "1.0", "scalaVersion": "2.12"}
        self.assertEqual(resolved_modules(self.report(module)), {("example.org:sbt-plugin_2.12_1.0", "1.0")})
        module["module"]["name"] += "_2.12_1.0"
        self.assertEqual(resolved_modules(self.report(module)), {("example.org:sbt-plugin_2.12_1.0", "1.0")})

    def test_missing_or_empty_resolution_cannot_pass(self):
        for report in [self.report(), self.report(self.module(missingArtifacts=[{}]))]:
            with self.assertRaises(ValueError):
                resolved_modules(report)
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                collect(Path(directory), Path(directory))

    def test_jar_properties_include_shaded_maven_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.jar"
            with zipfile.ZipFile(path, "w") as jar:
                for name in ["outer", "embedded"]:
                    jar.writestr("META-INF/maven/example.org/" + name + "/pom.properties",
                                 "groupId=example.org\nartifactId=" + name + "\nversion=1.2.3\n")
            self.assertEqual(jar_modules(path), {("example.org:outer", "1.2.3"), ("example.org:embedded", "1.2.3")})

    def test_unidentified_bootstrap_jar_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "unknown-1.0.jar"
            with zipfile.ZipFile(path, "w") as jar:
                jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
            with self.assertRaises(ValueError):
                jar_modules(path)

    def test_versionless_scala_bootstrap_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "scala-library.jar"
            with zipfile.ZipFile(path, "w") as jar:
                jar.writestr("META-INF/MANIFEST.MF", "Bundle-Version: 2.12.21.v20251211-VFINAL\n")
            self.assertEqual(jar_modules(path), {("org.scala-lang:scala-library", "2.12.21")})


if __name__ == "__main__":
    unittest.main()
