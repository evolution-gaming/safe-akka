import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/safe-akka")),
  startYear := Some(2017),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("http://evolutiongaming.com")),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.7", "2.12.12"),
  Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings"),
  publishTo := Some(Resolver.evolutionReleases),
  resolvers += Resolver.bintrayRepo("dnvriend", "maven"),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true)


lazy val safeAkka = (project
  in file(".")
  settings (name := "safe-akka")
  settings commonSettings
  settings (publish / skip  := true)
  aggregate(
    safeActor,
    safePersistence,
    safePersistenceTestkit,
    safePersistenceAsync))

lazy val safeActor = (project
  in file("safe-actor")
  settings (name := "safe-actor")
  settings commonSettings
  settings (
    libraryDependencies ++= Seq(
      Akka.actor,
      Akka.testkit % Test,
      Akka.stream % Test,
      Akka.`persistence-query` % Test,
      scalatest % Test,
      `executor-tools`,
      nel)))

lazy val safePersistence = (project
  in file("safe-persistence")
  settings (name := "safe-persistence")
  settings commonSettings
  dependsOn safeActor % "test->test;compile->compile"
  settings (
    scalacOptsFailOnWarn := Some(false),
    libraryDependencies ++= Seq(
      Akka.persistence,
      Akka.slf4j % Test,
      `akka-persistence-inmemory` % Test,
      `logback-classic` % Test,
      Slf4j.api % Test,
      Slf4j.`log4j-over-slf4j` % Test)))

lazy val safePersistenceAsync = (project
  in file("safe-persistence-async")
  settings (name := "safe-persistence-async")
  settings commonSettings
  dependsOn safePersistence % "test->test;compile->compile"
  settings (
    libraryDependencies ++= Seq(
      Akka.stream,
      Cats.core)))

lazy val safePersistenceTestkit = (project
  in file("safe-persistence-testkit")
  settings (name := "safe-persistence-testkit")
  settings commonSettings
  dependsOn(safeActor % "test->test;compile->compile", safePersistence))