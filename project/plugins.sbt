resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.2")

// Newer releases conflict with sbtix's uPickle major version during lock generation.
// Retain the working publishing plugin; its resolved graph is included in the audit.
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.11.0")

// dependencyTree is built into sbt; the obsolete external plugin is unnecessary.

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")
