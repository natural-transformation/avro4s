from pathlib import Path
import tempfile
import unittest
import zipfile
from dependency_inventory import check_bootstrap_classpath, collect, jar_modules, resolved_modules, resolved_jar_urls


class DependencyInventoryTest(unittest.TestCase):
    def module(self, name="library", version="1.0", **kwargs):
        return {"module": {"organization": "example.org", "name": name, "revision": version},
                "evicted": False, "artifacts": [[{}, "file:///a.jar"]], "missingArtifacts": [], **kwargs}

    def report(self, *modules):
        return {"configurations": [{"modules": list(modules)}]}

    def test_evicted_versions_are_excluded_but_selected_test_dependencies_remain(self):
        report = self.report(self.module(version="0.9", evicted=True), self.module(), self.module("test-library"))
        self.assertEqual(resolved_modules(report), {("example.org:library", "1.0"), ("example.org:test-library", "1.0")})

    def test_bootstrap_selects_only_resolved_compile_jars(self):
        normal = {"name": "library", "extension": "jar"}
        jar = self.module(artifacts=[[normal, "file:///library-1.0.jar"],
                                    [dict(normal, classifier="tests"), "file:///library-1.0-tests.jar"]])
        report = {"configurations": [
            {"configuration": {"name": "compile"}, "modules": [jar, self.module(version="0.9", evicted=True)]},
            {"configuration": {"name": "test"}, "modules": [self.module("test-only")]}
        ]}
        self.assertEqual(resolved_jar_urls(report),
                         {"https://repo1.maven.org/maven2/example/org/library/1.0/library-1.0.jar"})

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

    def test_shaded_metadata_does_not_hide_the_outer_library(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "outer_2.12-1.2.3.jar"
            with zipfile.ZipFile(path, "w") as jar:
                jar.writestr("META-INF/MANIFEST.MF",
                             "Implementation-Vendor-Id: example.org\nImplementation-Version: 1.2.3\n")
                jar.writestr("META-INF/maven/example.org/embedded/pom.properties",
                             "groupId=example.org\nartifactId=embedded\nversion=4.5.6\n")
            self.assertEqual(jar_modules(path),
                             {("example.org:outer_2.12", "1.2.3"), ("example.org:embedded", "4.5.6")})

    def test_versionless_scala_bootstrap_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "scala-library.jar"
            with zipfile.ZipFile(path, "w") as jar:
                jar.writestr("META-INF/MANIFEST.MF", "Bundle-Version: 2.12.21.v20251211-VFINAL\n")
            self.assertEqual(jar_modules(path), {("org.scala-lang:scala-library", "2.12.21")})

    def test_scala_compiler_and_its_embedded_dependencies_are_both_identified(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "scala-compiler.jar"
            with zipfile.ZipFile(path, "w") as jar:
                jar.writestr("META-INF/MANIFEST.MF", "Bundle-Version: 2.12.21.v20251211-VFINAL\n")
                jar.writestr("META-INF/maven/jline/jline/pom.properties",
                             "groupId=jline\nartifactId=jline\nversion=2.14.6\n")
            self.assertEqual(jar_modules(path), {("org.scala-lang:scala-compiler", "2.12.21"),
                                                ("jline:jline", "2.14.6")})

    def test_embedded_metadata_cannot_hide_an_unknown_outer_jar(self):
        for manifest in (None, "Manifest-Version: 1.0\n"):
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "unknown.jar"
                with zipfile.ZipFile(path, "w") as jar:
                    if manifest:
                        jar.writestr("META-INF/MANIFEST.MF", manifest)
                    jar.writestr("META-INF/maven/example.org/embedded/pom.properties",
                                 "groupId=example.org\nartifactId=embedded\nversion=1.0\n")
                with self.assertRaises(ValueError):
                    jar_modules(path)

    def test_actual_classpath_must_be_inside_the_scanned_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            boot = root / "boot"
            boot.mkdir()
            actual = root / "outside.jar"
            actual.touch()
            classpath = root / "classpath.txt"
            classpath.write_text(str(actual) + "\n")
            with self.assertRaises(ValueError):
                check_bootstrap_classpath(boot, classpath)
            actual.rename(boot / actual.name)
            classpath.write_text(str(boot / actual.name) + "\n")
            check_bootstrap_classpath(boot, classpath)
            classpath.write_text("")
            with self.assertRaises(ValueError):
                check_bootstrap_classpath(boot, classpath)


if __name__ == "__main__":
    unittest.main()
