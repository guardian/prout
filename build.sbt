name := "prout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.16"

updateOptions := updateOptions.value.withCachedResolution(true)

buildInfoKeys := Seq[BuildInfoKey](
  name,
  "gitCommitId" -> Option(System.getenv("SOURCE_VERSION")).getOrElse("unknown")
)

buildInfoPackage := "app"

lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin)

Test / testOptions +=
  Tests.Argument(TestFrameworks.ScalaTest, "-u", s"test-results/scala-${scalaVersion.value}")

val playGitHubVersion = "8.0.0-PREVIEW.add-support-for-getting-authenticated-github-app.2025-08-14T1126.653346d9"

val jacksonVersion         = "2.19.2"
val jacksonDatabindVersion = "2.19.2"

val jacksonOverrides = Seq(
  "com.fasterxml.jackson.core"     % "jackson-core",
  "com.fasterxml.jackson.core"     % "jackson-annotations",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
).map(_ % jacksonVersion)

val jacksonDatabindOverrides = Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion
)

val akkaSerializationJacksonOverrides = Seq(
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor",
  "com.fasterxml.jackson.module"     % "jackson-module-parameter-names",
  "com.fasterxml.jackson.module"     %% "jackson-module-scala",
).map(_ % jacksonVersion)

libraryDependencies ++= jacksonDatabindOverrides ++ jacksonOverrides ++ akkaSerializationJacksonOverrides

libraryDependencies ++= Seq(
  filters,
  ws,
  "com.softwaremill.macwire" %% "macros" % "2.6.6" % Provided, // slight finesse: 'provided' as only used for compile
  "com.madgag" %% "scala-collection-plus" % "1.0.0",
  "org.typelevel" %% "cats-core" % "2.13.0",
  "com.github.blemale" %% "scaffeine" % "5.3.0",
  "org.webjars" % "bootstrap" % "3.4.1",
  "com.getsentry.raven" % "raven-logback" % "8.0.3",
  // Transient dependency of raven-logback 8.0.3. No newer version of raven-logback available.
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "com.github.nscala-time" %% "nscala-time" % "3.0.0",
  "com.indoorvivants" %% "scala-uri" % "4.2.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "com.madgag.play-git-hub" %% "core" % playGitHubVersion,
  "com.madgag.play-git-hub" %% "testkit" % playGitHubVersion % Test,
  "com.madgag.scala-git" %% "scala-git-test" % "6.0.0" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
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