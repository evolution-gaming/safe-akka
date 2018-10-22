import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/safe-akka")),
  startYear := Some(2017),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("http://evolutiongaming.com")),
  bintrayOrganization := Some("evolutiongaming"),
  scalaVersion := crossScalaVersions.value.last,
  crossScalaVersions := Seq("2.11.12", "2.12.7"),
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xfuture"),
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
  ScalaTest, ExecutorTools)))

lazy val safePersistence = (project
  in file("safe-persistence")
  settings (name := "safe-persistence")
  settings commonSettings
  dependsOn safeActor % "test->test;compile->compile"
  settings (libraryDependencies ++= Seq(Akka.persistence, PersistenceInmemory)))

lazy val safePersistenceTestkit = (project
  in file("safe-persistence-testkit")
  settings (name := "safe-persistence-testkit")
  settings commonSettings
  dependsOn(safeActor % "test->test;compile->compile", safePersistence))