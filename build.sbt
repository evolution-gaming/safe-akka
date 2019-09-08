import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/safe-akka")),
  startYear := Some(2017),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("http://evolutiongaming.com")),
  bintrayOrganization := Some("evolutiongaming"),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.0", "2.12.9"),
  scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits", "-no-link-warnings"),
  resolvers += Resolver.bintrayRepo("evolutiongaming", "maven"),
  resolvers += Resolver.bintrayRepo("dnvriend", "maven"),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true)


lazy val safeAkka = (project
  in file(".")
  settings (name := "safe-akka")
  settings commonSettings
  aggregate(safeActor, safePersistence, safePersistenceTestkit))

lazy val safeActor = (project
  in file("safe-actor")
  settings (name := "safe-actor")
  settings commonSettings
  settings (libraryDependencies ++= Seq(
    Akka.actor, Akka.testkit % Test, Akka.stream % Test, Akka.`persistence-query` % Test,
    scalatest % Test,
    `executor-tools`,
    nel)))

lazy val safePersistence = (project
  in file("safe-persistence")
  settings (name := "safe-persistence")
  settings commonSettings
  dependsOn safeActor % "test->test;compile->compile"
  settings (libraryDependencies ++= Seq(
    Akka.persistence,
    Akka.slf4j % Test,
    `akka-persistence-inmemory` % Test,
    `logback-classic` % Test,
    Slf4j.api % Test,
    Slf4j.`log4j-over-slf4j` % Test)))

lazy val safePersistenceTestkit = (project
  in file("safe-persistence-testkit")
  settings (name := "safe-persistence-testkit")
  settings commonSettings
  dependsOn(safeActor % "test->test;compile->compile", safePersistence))