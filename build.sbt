import Dependencies.*

// Your next release will be binary compatible with the previous one,
// but it may not be source compatible (ie, it will be a minor release).
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible

/*
versionPolicyReportDependencyIssues ignored dependencies when compared to safe-akka 3.1.0.
All of those should not affect the library users, binary compatibility should be preserved.

Remember to clear up after 3.1.1 release!
 */
ThisBuild / versionPolicyIgnored ++= Seq(
  /*
  Examples:

  //com.chuusai:shapeless_2.13: missing dependency
  "com.chuusai" %% "shapeless",
  //org.scala-lang.modules:scala-java8-compat_2.13:
  //  incompatible version change from 0.9.0 to 1.0.0 (compatibility: early semantic versioning)
  "org.scala-lang.modules" %% "scala-java8-compat",
   */
)

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(url("https://github.com/evolution-gaming/safe-akka")),
  startYear := Some(2017),
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.16", "3.3.4"),
  Compile / scalacOptions ++= {
    if (scalaBinaryVersion.value == "2.13") Seq("-Xsource:3")
    else Seq("-explain", "-explain-types")
  },
  Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings"),
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))))

val alias: Seq[sbt.Def.Setting[?]] =
  addCommandAlias("check", "+all versionPolicyCheck Compile/doc") ++
    addCommandAlias("build", "+all compile test")

lazy val safeAkka = project
  .in (file("."))
  .settings (name := "safe-akka")
  .settings (commonSettings)
  .settings (alias)
  .settings (publish / skip  := true)
  .aggregate(
    safeActor,
    safePersistence,
    safePersistenceTestkit,
    safePersistenceAsync)

lazy val safeActor = project
  .in (file("safe-actor"))
  .settings (name := "safe-actor")
  .settings (commonSettings)
  .settings (
    libraryDependencies ++= Seq(
      Akka.actor,
      Akka.testkit % Test,
      Akka.stream % Test,
      Akka.`persistence-query` % Test,
      scalatest % Test,
      Akka.slf4j % Test,
      `logback-classic` % Test,
      Slf4j.api % Test,
      nel))

lazy val safePersistence = project
  .in (file("safe-persistence"))
  .settings (name := "safe-persistence")
  .settings (commonSettings)
  .dependsOn (safeActor % "test->test;compile->compile")
  .settings (
    libraryDependencies ++= Seq(
      Akka.persistence,
      Akka.`persistence-tck` % Test))

lazy val safePersistenceAsync = project
  .in (file("safe-persistence-async"))
  .settings (name := "safe-persistence-async")
  .settings (commonSettings)
  .dependsOn (safePersistence % "test->test;compile->compile")
  .settings (
    libraryDependencies ++= Seq(
      Akka.stream,
      Cats.core))

lazy val safePersistenceTestkit = project
  .in (file("safe-persistence-testkit"))
  .settings (name := "safe-persistence-testkit")
  .settings (commonSettings)
  .dependsOn(safeActor % "test->test;compile->compile", safePersistence)