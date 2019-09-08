import sbt._

object Dependencies {

  object Akka {
    private val version = "2.5.25"
    val actor               = "com.typesafe.akka" %% "akka-actor"             % version
    val persistence         = "com.typesafe.akka" %% "akka-persistence"       % version
    val testkit             = "com.typesafe.akka" %% "akka-testkit"           % version
    val stream              = "com.typesafe.akka" %% "akka-stream"            % version
    val `persistence-query` = "com.typesafe.akka" %% "akka-persistence-query" % version
    val slf4j               = "com.typesafe.akka" %% "akka-slf4j"             % version
  }

  object Slf4j {
    private val version = "1.7.28"
    val api                 = "org.slf4j" % "slf4j-api"        % version
    val `log4j-over-slf4j`  = "org.slf4j" % "log4j-over-slf4j" % version
  }

  val scalatest                   = "org.scalatest"       %% "scalatest"                 % "3.0.8"
  val `logback-classic`           = "ch.qos.logback"       % "logback-classic"           % "1.2.3"
  val `akka-persistence-inmemory` = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2"
  val `executor-tools`            = "com.evolutiongaming" %% "executor-tools"            % "1.0.2"
  val nel                         = "com.evolutiongaming" %% "nel"                       % "1.3.4"
}
