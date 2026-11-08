import os
from pathlib import Path
import subprocess
import unittest


class SbtOptionsTest(unittest.TestCase):
    def filtered(self, value):
        script = Path(__file__).with_name("filter_sbt_options.sh")
        return subprocess.check_output(["bash", str(script)], text=True,
                                       env={**os.environ, "SBT_OPTS": value}).split()

    def test_multiline_offline_options_are_preserved(self):
        value = """
          -Dsbt.ivy.home=./.ivy2
          -Dsbt.boot.directory=./.sbt-boot
          -Dsbt.override.build.repos=true
          -Dsbt.repository.config=/nix/store/repository
          -Dsbt.offline=true
        """
        self.assertEqual(self.filtered(value), ["-Dsbt.ivy.home=./.ivy2", "-Dsbt.override.build.repos=true",
                                               "-Dsbt.repository.config=/nix/store/repository", "-Dsbt.offline=true"])

    def test_only_conflicting_boot_flags_are_removed(self):
        self.assertEqual(self.filtered("-Xmx2G -Dsbt.boot.directory=/old -J-Dsbt.boot.directory=/other -Dkeep=true"),
                         ["-Xmx2G", "-Dkeep=true"])
        self.assertEqual(self.filtered(""), [])
