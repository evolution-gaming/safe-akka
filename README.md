# Safe Akka [![Build Status](https://travis-ci.org/evolution-gaming/safe-akka.svg)](https://travis-ci.org/evolution-gaming/safe-akka) [![Coverage Status](https://coveralls.io/repos/evolution-gaming/safe-akka/badge.svg)](https://coveralls.io/r/evolution-gaming/safe-akka) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/8457f5d789694d31b8e8c34e6b9f5e14)](https://www.codacy.com/app/t3hnar/safe-akka?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=evolution-gaming/safe-akka&amp;utm_campaign=Badge_Grade) [![version](https://api.bintray.com/packages/evolutiongaming/maven/safe-akka/images/download.svg)](https://bintray.com/evolutiongaming/maven/safe-akka/_latestVersion) [![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)

This library provides abstraction on top of akka actors in order to add more type safety

Here is a small example:

```scala
  import com.evolutiongaming.safeakka.actor._
  
  val ref = SafeActorRef(_ => (counter(0), ActorLog.empty))
    
  ref ! Msg.Inc
  ref ! Msg.Dec
  ref ! PoisonPill
  // ref ! "test" - does not compile

  def counter(state: Int): Behavior[Msg] = Behavior.onMsg[Msg] {
    case Signal.Msg(msg, sender) =>
      val result = msg match {
        case Msg.Inc => state + 1
        case Msg.Dec => state - 1
      }
      sender ! result
      counter(result)
  }


  sealed trait Msg

  object Msg {
    case object Inc extends Msg
    case object Dec extends Msg
  }
```


## Setup

```scala
resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")

libraryDependencies += "com.evolutiongaming" %% "safe-actor" % "1.8.1"

libraryDependencies += "com.evolutiongaming" %% "safe-persistence" % "1.8.1"

libraryDependencies += "com.evolutiongaming" %% "safe-persistence-testkit" % "1.8.1"
``` 