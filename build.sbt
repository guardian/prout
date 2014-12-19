name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.4"

updateOptions := updateOptions.value.withCachedResolution(true)

herokuAppName in Compile := "prout-bot"

herokuJdkVersion in Compile := "1.8"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](
  name,
  BuildInfoKey.constant("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse(try {
    "git rev-parse HEAD".!!.trim
  } catch {
    case e: Exception => "unknown"
  }))
)

buildInfoPackage := "app"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  cache,
  filters,
  ws,
  "com.typesafe.akka" %% "akka-agent" % "2.3.2",
  "org.webjars" % "bootstrap" % "3.3.1",
  "com.madgag" % "github-api" % "1.59.99.1",
  "com.github.nscala-time" %% "nscala-time" % "1.6.0",
  "com.netaporter" %% "scala-uri" % "0.4.4",
  "com.squareup.okhttp" % "okhttp" % "2.1.0",
  "com.squareup.okhttp" % "okhttp-urlconnection" % "2.1.0",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.5.3.201412180710-r",
  "com.madgag.scala-git" %% "scala-git" % "2.7",
  "com.madgag.scala-git" %% "scala-git-test" % "2.5" % "test",
  "org.scalatestplus" %% "play" % "1.1.0" % "test"
)     

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

