import scala.util.Try

name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.1"

updateOptions := updateOptions.value.withCachedResolution(true)

resolvers += Resolver.sonatypeRepo("releases")

buildInfoKeys := Seq[BuildInfoKey](
  name,
  BuildInfoKey.constant("gitCommitId", Option(System.getenv("SOURCE_VERSION")).getOrElse("unknown"))
)

buildInfoPackage := "app"

lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin)

libraryDependencies ++= Seq(
  ehcache,
  filters,
  ws,
  "com.typesafe.akka" %% "akka-agent" % "2.5.26",
  "org.webjars" % "bootstrap" % "3.3.2-1",
  "com.getsentry.raven" % "raven-logback" % "8.0.2",
  "com.github.nscala-time" %% "nscala-time" % "2.22.0",
  "io.lemonlabs" %% "scala-uri" % "1.5.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "org.webjars.bower" % "octicons" % "3.1.0",
  "com.madgag" %% "play-git-hub" % "4.7-SNAPSHOT",
  "com.madgag.scala-git" %% "scala-git-test" % "4.3" % "test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test"
)

routesImport ++= Seq("com.madgag.scalagithub.model._","com.madgag.playgithub.Binders._")

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

