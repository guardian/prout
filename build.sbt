name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.10"

updateOptions := updateOptions.value.withCachedResolution(true)

resolvers ++= Resolver.sonatypeOssRepos("releases")

buildInfoKeys := Seq[BuildInfoKey](
  name,
  "gitCommitId" -> Option(System.getenv("SOURCE_VERSION")).getOrElse("unknown")
)

buildInfoPackage := "app"

lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin)

Test / testOptions +=
  Tests.Argument(TestFrameworks.ScalaTest, "-u", s"test-results/scala-${scalaVersion.value}")

libraryDependencies ++= Seq(
  filters,
  ws,
  "com.softwaremill.macwire" %% "macros" % "2.5.8" % Provided, // slight finesse: 'provided' as only used for compile
  "com.madgag" %% "scala-collection-plus" % "0.11",
  "org.typelevel" %% "cats-core" % "2.9.0",
  "com.github.blemale" %% "scaffeine" % "5.2.1",
  "org.webjars" % "bootstrap" % "3.3.2-1",
  "com.getsentry.raven" % "raven-logback" % "8.0.3",
  "com.github.nscala-time" %% "nscala-time" % "2.32.0",
  "io.lemonlabs" %% "scala-uri" % "4.0.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "org.webjars.bower" % "octicons" % "3.1.0",
  "com.madgag" %% "play-git-hub" % "5.3",
  "com.madgag.scala-git" %% "scala-git-test" % "4.6" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
)

routesImport ++= Seq("com.madgag.scalagithub.model._","com.madgag.playgithub.Binders._")

Compile/doc/sources := Seq.empty
Compile/packageDoc/publishArtifact := false