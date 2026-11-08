import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype._
import xerial.sbt.Sonatype.autoImport._

/** Adds common settings automatically to all subprojects */
object Build extends AutoPlugin {

  object autoImport {
    val org = "com.natural-transformation"
    val AvroVersion = "1.12.2"
    val ScalatestVersion = "3.2.20"
    val Slf4jVersion = "2.0.20"
    val Json4sVersion = "4.0.7"
    val CatsVersion = "2.13.0"
    val MagnoliaVersion = "1.3.24"
    val KafkaVersion = "4.3.2"
    // Keep >= AvroVersion's Jackson BOM (1.12.2 uses 2.22.1). Recheck when Avro
    // changes: Maven's nearest-wins resolution can otherwise retain an older pin.
    val JacksonVersion = "2.22.4"
    val exportBootstrapClasspath = taskKey[File]("Record the sbt launcher's actual bootstrap classpath for the security audit")
  }

  import autoImport._

  def releaseVersion: String = sys.env.getOrElse("RELEASE_VERSION", "")
  def isRelease: Boolean = releaseVersion != ""
  private val BaseVersion: String = "5.2.0"
  def publishVersion: String = if (isRelease) releaseVersion else s"$BaseVersion-SNAPSHOT"

  private val UseMavenLocalEnvVar: String = "AVRO4S_USE_MAVEN_LOCAL"
  private def useMavenLocalResolver: Boolean = sys.env.get(UseMavenLocalEnvVar).contains("1")

  // OSSRH (Nexus 2) is sunset; publishing happens via Sonatype Central.
  //
  // - SNAPSHOTs: publish directly to the Central Portal snapshots repository.
  // - Releases: use sbt-sonatype's *Central* commands (sonatypeCentralUpload/sonatypeCentralRelease),
  //   which talk to the OSSRH Staging API Service (a compatibility layer backed by Central).
  //
  // Docs:
  // - https://central.sonatype.org/pages/ossrh-eol/
  // - https://central.sonatype.org/publish/publish-portal-snapshots/
  private val CentralPortalHost           = "central.sonatype.com"
  private val CentralPortalSnapshotsRepo  = "https://central.sonatype.com/repository/maven-snapshots/"
  private val CentralNexusRealm           = "Sonatype Nexus Repository Manager"
  private val OssrhStagingApiHost         = "ossrh-staging-api.central.sonatype.com"
  private val OssrhStagingApiServiceLocal = "https://ossrh-staging-api.central.sonatype.com/service/local"
  private val OssrhStagingApiRealm        = "OSSRH Staging API Service"

  // Publishing credentials (env vars):
  // - SONATYPE_CENTRAL_*: Central Portal snapshots repository (central.sonatype.com)
  // - SONATYPE_STAGING_*: OSSRH Staging API Service for releases (ossrh-staging-api.central.sonatype.com)
  private val CentralUserEnvVar: String  = "SONATYPE_CENTRAL_USERNAME"
  private val CentralPassEnvVar: String  = "SONATYPE_CENTRAL_PASSWORD"
  private val StagingUserEnvVar: String  = "SONATYPE_STAGING_USERNAME"
  private val StagingPassEnvVar: String  = "SONATYPE_STAGING_PASSWORD"

  private def envUserPass(userKey: String, passKey: String): Option[(String, String)] = {
    val user = sys.env.getOrElse(userKey, "")
    val pass = sys.env.getOrElse(passKey, "")
    if (user.nonEmpty && pass.nonEmpty) Some((user, pass)) else None
  }

  override def trigger = allRequirements
  override def projectSettings = commonSettings ++ publishingSettings 
  
  val commonSettings = Seq(
    exportBootstrapClasspath / aggregate := false,
    exportBootstrapClasspath := {
      val provider = appConfiguration.value.provider
      val jars = (provider.mainClasspath.toSeq ++ provider.scalaProvider.jars.toSeq).distinct
      val output = (LocalRootProject / baseDirectory).value / "target" / "dependency-audit" / "bootstrap-classpath.txt"
      IO.writeLines(output, jars.map(_.getAbsolutePath))
      // sbt wraps provider.scalaProvider.launcher in a proxy from main_2.12.
      // Inspect the JVM's actual launcher entry point, not that application proxy.
      val launcherMain = ClassLoader.getSystemClassLoader.loadClass("xsbt.boot.Boot")
      val launcher = new File(launcherMain.getProtectionDomain.getCodeSource.getLocation.toURI)
      IO.write(output.getParentFile / "launcher-path.txt", launcher.getAbsolutePath + "\n")
      output
    },
    organization       := org,
    scalaVersion := "3.3.9",
    // For reproducible builds, we avoid `mavenLocal` unless explicitly enabled.
    // Set AVRO4S_USE_MAVEN_LOCAL=1 to opt in.
    resolvers ++= (if (useMavenLocalResolver) Seq(Resolver.mavenLocal) else Nil),
    Test / parallelExecution := false,
    Test / scalacOptions ++= Seq("-Xmax-inlines:100", "-Yretain-trees"),
    // Keep both bytecode and JDK API usage compatible with Java 21 when built on 25.
    scalacOptions += "-release:21",
    javacOptions := Seq("--release", "21"),
    // These also patch the separate compiler/Scaladoc configurations. Keep until
    // an upstream Scala update resolves safe versions; they are audited in CI.
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-annotations" % "2.22",
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % JacksonVersion,
      "org.jsoup" % "jsoup" % "1.24.1"
    ) ++ Seq("jline-reader", "jline-terminal", "jline-terminal-jni", "jline-native")
      .map("org.jline" % _ % "3.30.15"),
    libraryDependencies ++= Seq(
      "org.apache.avro"   % "avro"              % AvroVersion,
      // Publish patched Jackson requirements so downstream Maven users inherit them.
      "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion,
      // Provide a no-op SLF4J provider in tests to avoid "No SLF4J providers were found" warnings.
      "org.slf4j"         % "slf4j-api"         % Slf4jVersion          % Test,
      "org.slf4j"         % "slf4j-nop"         % Slf4jVersion          % Test,
      "org.slf4j"         % "log4j-over-slf4j"  % Slf4jVersion          % Test,
      "org.scalatest"     % "scalatest_3"       % ScalatestVersion      % Test
    )
  )

  val publishingSettings = Seq(
    publishMavenStyle := true,
    Test / publishArtifact := false,

    credentials ++= {
      val credsFile = Path.userHome / ".sbt" / "sonatype_credentials"
      // We support either:
      // - ~/.sbt/sonatype_credentials (local)
      // - SONATYPE_CENTRAL_USERNAME / SONATYPE_CENTRAL_PASSWORD (Central Portal snapshots)
      // - SONATYPE_STAGING_USERNAME / SONATYPE_STAGING_PASSWORD (release publishing via OSSRH Staging API Service)
      val userPassFromFile: Option[(String, String)] =
        if (credsFile.exists()) {
          // We parse the file ourselves instead of using Credentials(file) because
          // `Credentials(file)` returns an internal credentials type that doesn't expose user/pass.
          val kv =
            IO.readLines(credsFile)
              .iterator
              .map(_.trim)
              .filter(l => l.nonEmpty && !l.startsWith("#"))
              .flatMap { l =>
                l.split("=", 2) match {
                  case Array(k, v) => Some((k.trim.toLowerCase, v.trim))
                  case _           => None
                }
              }
              .toMap

          val user = kv.get("user").orElse(kv.get("username")).getOrElse("")
          val pass = kv.getOrElse("password", "")

          if (user.nonEmpty && pass.nonEmpty) Some((user, pass)) else None
        } else None

      val centralUserPass: Option[(String, String)] =
        envUserPass(CentralUserEnvVar, CentralPassEnvVar)
          .orElse(userPassFromFile)

      val stagingUserPass: Option[(String, String)] =
        envUserPass(StagingUserEnvVar, StagingPassEnvVar)
          .orElse(userPassFromFile)

      val creds = Seq.newBuilder[Credentials]

      centralUserPass.foreach { case (user, pass) =>
        // Central Portal snapshots (for -SNAPSHOT versions)
        creds += Credentials(CentralNexusRealm, CentralPortalHost, user, pass)
      }

      stagingUserPass.foreach { case (user, pass) =>
        // OSSRH Staging API Service (for releases; backed by Central)
        creds += Credentials(OssrhStagingApiRealm, OssrhStagingApiHost, user, pass)
      }

      creds.result()
    },

    version := publishVersion,
    pomIncludeRepository := { _  => false },
    // For snapshots, publish directly to the Central Portal snapshots repository.
    // For releases, keep using sbt-sonatype's bundle flow (which uses the staging API host below).
    publishTo := {
      if (isSnapshot.value) Some("central-portal-snapshots" at CentralPortalSnapshotsRepo)
      else sonatypePublishToBundle.value
    },
    // sbt-sonatype Central commands use the OSSRH Staging API Service (backed by Central).
    // sonatypeCentral* commands require the credential host to be `central.sonatype.com`.
    sonatypeCredentialHost := CentralPortalHost,
    sonatypeRepository := OssrhStagingApiServiceLocal,
    sonatypeProfileName := "com.natural-transformation",
    sonatypeProjectHosting := Some(GitHubHosting("natural-transformation", "avro4s", "zli@natural-transformation.com")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/natural-transformation/avro4s")),
    scmInfo := Some(ScmInfo(
      url("https://github.com/natural-transformation/avro4s"),
      "scm:git:git@github.com:natural-transformation/avro4s.git"
    )),
    developers := List(Developer(
      id = "natural-transformation",
      name = "Natural Transformation BV",
      email = "zli@natural-transformation.com",
      url = url("https://natural-transformation.com")
    ))
  )
}
