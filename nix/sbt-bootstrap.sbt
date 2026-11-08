// Resolve sbt itself with patched transitive dependencies. This graph becomes
// the actual bootstrap classpath used by the Nix-managed sbt wrapper.
scalaVersion := "2.12.21"
libraryDependencies += "org.scala-sbt" % "sbt" % sbtVersion.value
dependencyOverrides ++= Seq(
  "org.apache.logging.log4j" % "log4j-api" % "2.26.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.26.1",
  "org.jsoup" % "jsoup" % "1.24.1"
) ++ Seq("jline", "jline-builtins", "jline-reader", "jline-terminal",
  "jline-terminal-jni", "jline-terminal-jna", "jline-native")
  .map("org.jline" % _ % "3.30.15")
