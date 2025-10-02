import sbt.*

object Dependencies {

  object Akka {
    private val version = "2.6.21"
    val actor               = "com.typesafe.akka" %% "akka-actor"             % version
    val persistence         = "com.typesafe.akka" %% "akka-persistence"       % version
    val `persistence-tck`   = "com.typesafe.akka" %% "akka-persistence-tck"   % version
    val testkit             = "com.typesafe.akka" %% "akka-testkit"           % version
    val stream              = "com.typesafe.akka" %% "akka-stream"            % version
    val `persistence-query` = "com.typesafe.akka" %% "akka-persistence-query" % version
    val slf4j               = "com.typesafe.akka" %% "akka-slf4j"             % version
  }

  object Cats {
    private val version = "2.13.0"
    val core = "org.typelevel" %% "cats-core" % version
  }

  object Slf4j {
    private val version = "2.0.17"
    val api                = "org.slf4j" % "slf4j-api"        % version
  }

  val scalatest                   = "org.scalatest"       %% "scalatest"                 % "3.2.19"
  val `logback-classic`           = "ch.qos.logback"       % "logback-classic"           % "1.5.19"
  val nel                         = "com.evolutiongaming" %% "nel"                       % "1.3.5"
}
