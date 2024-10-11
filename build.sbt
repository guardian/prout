name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.14"

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
  "com.softwaremill.macwire" %% "macros" % "2.5.9" % Provided, // slight finesse: 'provided' as only used for compile
  "com.madgag" %% "scala-collection-plus" % "0.11",
  "org.typelevel" %% "cats-core" % "2.10.0",
  "com.github.blemale" %% "scaffeine" % "5.3.0",
  "org.webjars" % "bootstrap" % "3.4.1",
  "com.getsentry.raven" % "raven-logback" % "8.0.3",
  // Transient dependency of raven-logback 8.0.3. No newer version of raven-logback available.
  "ch.qos.logback" % "logback-classic" % "1.5.9",
  "com.github.nscala-time" %% "nscala-time" % "2.34.0",
  "io.lemonlabs" %% "scala-uri" % "4.0.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "com.madgag.play-git-hub" %% "core" % "6.0",
  "com.madgag.play-git-hub" %% "testkit" % "6.0" % Test,
  "com.madgag.scala-git" %% "scala-git-test" % "4.8" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
)

// Overidden transient dependencies for Vulnerability fixes
libraryDependencies ++= Seq(
  // Introduced through org.webjars:bootstrap:3.4.1
  // Fix available in next major bootstrap version - this will involve a lot of breaking changes however.
  "org.webjars" % "jquery" % "3.7.1",
)

routesImport ++= Seq("com.madgag.scalagithub.model._","com.madgag.playgithub.Binders._")

Compile/doc/sources := Seq.empty
Compile/packageDoc/publishArtifact := false