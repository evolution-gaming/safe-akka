import sbt._

object Dependencies {

  object Akka {
    private val version = "2.5.17"

    val actor = "com.typesafe.akka" %% "akka-actor" % version
    val persistence = "com.typesafe.akka" %% "akka-persistence" % version
    val testkit = "com.typesafe.akka" %% "akka-testkit" % version
    val stream = "com.typesafe.akka" %% "akka-stream" % version
    val `persistence-query` = "com.typesafe.akka" %% "akka-persistence-query" % version
  }

  lazy val ScalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % Test
  lazy val PersistenceInmemory = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1" % Test
  
  lazy val ExecutorTools = "com.evolutiongaming" %% "executor-tools" % "1.0.1"
}
