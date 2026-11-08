resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.2")

// Newer releases conflict with sbtix's uPickle major version during lock generation.
// Retain the working publishing plugin; its resolved graph is included in the audit.
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.11.0")

// dependencyTree is built into sbt; the obsolete external plugin is unnecessary.

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")

// Security fixes for the publishing plugins' transitive dependencies. The CI
// inventory audits this build classpath as well as the published library graph.
dependencyOverrides ++= Seq(
  "ch.qos.logback" % "logback-core" % "1.5.34",
  "com.google.guava" % "guava" % "33.8.0-jre",
  "com.squareup.okhttp3" % "okhttp" % "4.12.0",
  "com.squareup.okio" % "okio" % "3.6.0",
  "org.apache.httpcomponents" % "httpclient" % "4.5.14",
  "org.msgpack" % "msgpack-core" % "0.9.12"
) ++ Seq("bcpg-jdk18on", "bcprov-jdk18on", "bcutil-jdk18on")
  .map("org.bouncycastle" % _ % "1.86")
