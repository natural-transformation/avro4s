// Resolve sbt itself with patched transitive dependencies. This graph becomes
// the actual bootstrap classpath used by the Nix-managed sbt wrapper.
// Follow the Scala runtime of the requested sbt version, including on upgrades.
scalaVersion := appConfiguration.value.provider.scalaProvider.version
libraryDependencies += "org.scala-sbt" % "sbt" % sbtVersion.value
dependencyOverrides ++= Seq(
  // 2.1.14 embeds jsoup 1.23.2; overriding external jsoup cannot fix a shaded copy.
  "io.get-coursier" % "lm-coursier-shaded_2.12" % "2.1.14",
  "org.apache.logging.log4j" % "log4j-api" % "2.26.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.26.1",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.26.1",
  "org.jsoup" % "jsoup" % "1.24.1"
) ++ Seq("jline", "jline-builtins", "jline-reader", "jline-terminal",
  "jline-terminal-jni", "jline-terminal-jna", "jline-native")
  .map("org.jline" % _ % "3.30.15")
