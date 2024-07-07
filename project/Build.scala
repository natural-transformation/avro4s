import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype._
import xerial.sbt.Sonatype.autoImport._

/** Adds common settings automatically to all subprojects */
object Build extends AutoPlugin {

  object autoImport {
    val org = "com.natural-transformation"
    val AvroVersion = "1.11.1"
    val Log4jVersion = "1.2.17"
    val ScalatestVersion = "3.2.16"
    val Slf4jVersion = "2.0.7"
    val Json4sVersion = "4.0.6"
    val CatsVersion = "2.10.0"
    val RefinedVersion = "0.9.26"
    val ShapelessVersion = "2.3.7"
    val MagnoliaVersion = "1.3.3"
    val SbtJmhVersion = "0.3.7"
    val JmhVersion = "1.32"
  }

  import autoImport._

  def isGithubActions: Boolean = sys.env.getOrElse("CI", "false") == "true"
  def releaseVersion: String = sys.env.getOrElse("RELEASE_VERSION", "5.0.0")
  def isRelease: Boolean = releaseVersion != ""
  def githubRunNumber: String = sys.env.getOrElse("GITHUB_RUN_NUMBER", "local")
  // def ossrhUsername: String = sys.env.getOrElse("OSSRH_USERNAME", "")
  // def ossrhPassword: String = sys.env.getOrElse("OSSRH_PASSWORD", "")
  def publishVersion: String = if (isRelease) releaseVersion else "5.0.0." + githubRunNumber + "-SNAPSHOT"

  override def trigger = allRequirements
  override def projectSettings = publishingSettings ++ Seq(
    organization       := "com.natural-transformation",
    scalaVersion := "3.3.1",
    resolvers += Resolver.mavenLocal,
    Test / parallelExecution := false,
    Test / scalacOptions ++= Seq("-Xmax-inlines:64"),
    javacOptions := Seq("-source", "21", "-target", "21"),    
    libraryDependencies ++= Seq(
      "org.scala-lang"    % "scala3-compiler_3" % scalaVersion.value,
      "org.apache.avro"   % "avro"              % AvroVersion,
      "org.slf4j"         % "slf4j-api"         % Slf4jVersion          % "test",
      "log4j"             % "log4j"             % Log4jVersion          % "test",
      "org.slf4j"         % "log4j-over-slf4j"  % Slf4jVersion          % "test",
      "org.scalatest"     % "scalatest_3"       % ScalatestVersion      % "test"
    )
  )

  val publishingSettings = Seq(
    publishMavenStyle := true,
    Test / publishArtifact := false,
    // credentials += Credentials(
    //   "Sonatype Nexus Repository Manager",
    //   "s01.oss.sonatype.org",
    //   ossrhUsername,
    //   ossrhPassword
    // ),
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
    version := publishVersion,
    // publishTo := {
    //   val nexus = "https://s01.oss.sonatype.org/"
    //   if (isRelease) {
    //     Some("releases" at s"${nexus}service/local/staging/deploy/maven2")
    //   } else {
    //     Some("snapshots" at s"${nexus}content/repositories/snapshots")
    //   }
    // }
    licenses      := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    pomIncludeRepository   := { _ => false },
    publishTo := sonatypePublishToBundle.value,
    sonatypeCredentialHost := "s01.oss.sonatype.org",
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
