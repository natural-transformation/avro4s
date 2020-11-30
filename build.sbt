lazy val root = project
  .in(file("."))
  .aggregate(core)
  .settings(
    name := "avro4s",
    version := "5.0.0",
    scalaVersion := "3.0.0-M2",
    useScala3doc := true
  )

lazy val core = project
  .in(file("avro4s-core"))
  .settings(
    name := "avro4s-core",
    version := "5.0.0",
    scalaVersion := "3.0.0-M2",
    useScala3doc := true,
    libraryDependencies ++= List(
      "org.apache.avro" % "avro" % "1.10.0",
      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
  )