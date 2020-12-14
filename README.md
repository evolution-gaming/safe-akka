# Safe Akka
[![Build Status](https://github.com/evolution-gaming/safe-akka/workflows/CI/badge.svg)](https://github.com/evolution-gaming/safe-akka/actions?query=workflow%3ACI)
[![Coverage Status](https://coveralls.io/repos/evolution-gaming/safe-akka/badge.svg)](https://coveralls.io/r/evolution-gaming/safe-akka)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/8457f5d789694d31b8e8c34e6b9f5e14)](https://www.codacy.com/app/t3hnar/safe-akka?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=evolution-gaming/safe-akka&amp;utm_campaign=Badge_Grade)
[![version](https://api.bintray.com/packages/evolutiongaming/maven/safe-akka/images/download.svg)](https://bintray.com/evolutiongaming/maven/safe-akka/_latestVersion)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)

This library provides abstraction on top of akka actors in order to add more type safety

## Actor example

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

## PersistentActor example

```scala
def persistenceSetup(ctx: ActorCtx) = {

  ctx.setReceiveTimeout(300.millis)

  new PersistenceSetup[Counter, Counter, Cmd, Event] {

    val persistenceId = UUID.randomUUID().toString

    val log = ActorLog.empty

    def journalId = None

    def snapshotId = None

    def onRecoveryStarted(
      offer: Option[SnapshotOffer[Counter]],
      journaller: Journaller,
      snapshotter: Snapshotter[Counter]) = new Recovering {

      def state = offer map { _.snapshot } getOrElse Counter(0, 0)

      def eventHandler(state: Counter, event: Event, seqNr: SeqNr) = state(event, seqNr)

      def onCompleted(state: Counter, seqNr: SeqNr) = {

        def behavior(counter: Counter): Behavior[Cmd, Event] = Behavior[Cmd, Event] { (signal, _) =>
          signal match {
            case signal: PersistenceSignal.System =>
              testActor.tell(signal, ctx.self)
              signal match {
                case PersistenceSignal.Sys(Signal.RcvTimeout) => ctx.setReceiveTimeout(Duration.Inf)
                case _                                        =>
              }
              behavior(counter)

            case PersistenceSignal.Cmd(cmd, sender) =>

              def onEvent(event: Event) = {

                val record = Record.of(event)(_ => sender.tell(event, ctx.self))
                val onPersisted = (seqNr: SeqNr) => {
                  val newCounter = counter(event, seqNr)
                  sender.tell(newCounter, ctx.self)
                  if (cmd == Cmd.Dec) snapshotter.save(seqNr, newCounter)
                  behavior(newCounter)
                }

                val onFailure = (failure: Throwable) => {
                  sender.tell(Status.Failure(failure), ctx.self)
                }

                Behavior.persist(Nel(record), onPersisted, onFailure)
              }

              cmd match {
                case Cmd.Inc  => onEvent(Event.Inc)
                case Cmd.Dec  => onEvent(Event.Dec)
                case Cmd.Stop => Behavior.stop
                case Cmd.Get  =>
                  sender.tell(counter, ctx.self)
                  behavior(counter)
              }
          }
        }

        behavior(state)
      }

      def onStopped(state: Counter, seqNr: SeqNr) = {}
    }

    def onStopped(seqNr: SeqNr): Unit = {}
  }
}


val ref = PersistentActorRef(persistenceSetup)
ref ! Cmd.Get
ref ! Cmd.Inc
ref ! Cmd.Dec
ref ! Cmd.Stop

```

See [CounterSpec](safe-persistence/src/test/scala/com/evolutiongaming/safeakka/persistence/CounterSpec.scala)

## Setup

```scala
resolvers += Resolver.bintrayRepo("evolutiongaming", "maven")

libraryDependencies += "com.evolutiongaming" %% "safe-actor" % "2.1.0"

libraryDependencies += "com.evolutiongaming" %% "safe-persistence" % "2.1.0"

libraryDependencies += "com.evolutiongaming" %% "safe-persistence-async" % "2.1.0"

libraryDependencies += "com.evolutiongaming" %% "safe-persistence-testkit" % "2.1.0"
``` 