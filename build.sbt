name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

updateOptions := updateOptions.value.withCachedResolution(true)

resolvers += Resolver.sonatypeRepo("releases")

buildInfoKeys := Seq[BuildInfoKey](
  name,
  BuildInfoKey.constant("gitCommitId", Option(System.getenv("SOURCE_VERSION")) getOrElse(try {
    "git rev-parse HEAD".!!.trim
  } catch { case e: Exception => "unknown" }))
)

buildInfoPackage := "app"

lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin)

libraryDependencies ++= Seq(
  cache,
  filters,
  ws,
  "com.typesafe.akka" %% "akka-agent" % "2.3.2",
  "org.webjars" % "bootstrap" % "3.3.2-1",
  "com.getsentry.raven" % "raven-logback" % "7.6.0",
  "com.github.nscala-time" %% "nscala-time" % "2.0.0",
  "com.netaporter" %% "scala-uri" % "0.4.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1",
  "org.webjars.bower" % "octicons" % "3.1.0",
  "com.madgag" %% "play-git-hub" % "3.27",
  "com.madgag.scala-git" %% "scala-git-test" % "3.0" % "test",
  "org.scalatestplus" %% "play" % "1.4.0" % "test"
)

routesImport ++= Seq("com.madgag.scalagithub.model._","com.madgag.playgithub.Binders._")

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

