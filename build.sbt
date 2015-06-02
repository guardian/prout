name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

updateOptions := updateOptions.value.withCachedResolution(true)

buildInfoKeys := Seq[BuildInfoKey](
  name,
  BuildInfoKey.constant("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse(try {
    "git rev-parse HEAD".!!.trim
  } catch {
    case e: Exception => "unknown"
  }))
)

buildInfoPackage := "app"

lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin)

libraryDependencies ++= Seq(
  cache,
  filters,
  ws,
  "com.typesafe.akka" %% "akka-agent" % "2.3.2",
  "org.webjars" % "bootstrap" % "3.3.2-1",
  "org.kohsuke" % "github-api" % "1.66" exclude("org.jenkins-ci", "annotation-indexer"),
  "com.github.nscala-time" %% "nscala-time" % "2.0.0",
  "com.netaporter" %% "scala-uri" % "0.4.7",
  "com.squareup.okhttp" % "okhttp" % "2.4.0",
  "com.squareup.okhttp" % "okhttp-urlconnection" % "2.4.0",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.7.1.201504261725-r",
  "com.madgag.scala-git" %% "scala-git" % "2.9",
  "com.madgag.scala-git" %% "scala-git-test" % "2.9" % "test",
  "org.scalatestplus" %% "play" % "1.4.0-M3" % "test"
)     

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

