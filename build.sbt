name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.11"

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
  "org.webjars" % "bootstrap" % "3.4.1",
  "com.getsentry.raven" % "raven-logback" % "8.0.3",
  "com.github.nscala-time" %% "nscala-time" % "2.32.0",
  "io.lemonlabs" %% "scala-uri" % "4.0.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "com.madgag.play-git-hub" %% "core" % "5.10",
  "com.madgag.play-git-hub" %% "testkit" % "5.10" % Test,
  "com.madgag.scala-git" %% "scala-git-test" % "4.6" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
)

// Overidden transient dependencies for Vulnerability fixes
libraryDependencies ++= Seq(
  // Introduced through com.typesafe.play:play_2.13:2.9.0
  // No newer version of play available yet.
  "com.typesafe.akka" %% "akka-actor" % "2.8.1",
  // Introduced through org.webjars:bootstrap:3.4.1
  // Fix available in next major bootstrap version - this will involve a lot of breaking changes however.
  "org.webjars" % "jquery" % "3.6.4",
  // Introduced through com.madgag.play-git-hub:core:5.10
  // No newer version of play-git-hub available yet.
  "org.eclipse.jgit" % "org.eclipse.jgit" % "6.6.1.202309021850-r",
  "com.squareup.okhttp3" % "okhttp" % "4.12.0"
)

routesImport ++= Seq("com.madgag.scalagithub.model._","com.madgag.playgithub.Binders._")

Compile/doc/sources := Seq.empty
Compile/packageDoc/publishArtifact := false