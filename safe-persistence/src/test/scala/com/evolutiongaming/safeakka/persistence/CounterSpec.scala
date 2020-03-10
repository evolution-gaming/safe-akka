package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.Status
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, Signal}
import com.evolutiongaming.safeakka.persistence.{PersistentBehavior => Behavior}

import scala.concurrent.duration._
import org.scalatest.wordspec.AnyWordSpec

class CounterSpec extends AnyWordSpec with ActorSpec {
  import CounterSpec._

  "Counter" should {

    "handle commands and persist events" in new Scope {
      ref ! Cmd.Get
      expectMsg(Counter(0, 0))

      expectMsg(PersistenceSignal.Sys(Signal.RcvTimeout))

      ref ! Cmd.Inc
      expectMsg(Event.Inc)
      expectMsg(Counter(1, 1))

      ref ! Cmd.Dec
      expectMsg(Event.Dec)
      expectMsg(Counter(0, 2))
      expectMsgType[PersistenceSignal.SaveSnapshotSuccess]

      ref ! Cmd.Stop
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))
    }

    "handle many commands" ignore new Scope {
      val n = 10000
      for {_ <- 0 to n} ref ! Cmd.Inc

      val expected = Counter(n, n.toLong)

      fishForMessage() {
        case Event.Inc  => false
        case `expected` => true
        case _: Counter => false
      }
    }
  }

  private trait Scope extends ActorScope {

    def persistenceSetup(ctx: ActorCtx) = {

      ctx.setReceiveTimeout(300.millis)

      new PersistenceSetup[Counter, Counter, Cmd, Event] {

        val persistenceId = UUID.randomUUID().toString

        val log = ActorLog(system, classOf[CounterSpec]).prefixed(persistenceId)

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
  }
}

object CounterSpec {
  case class Counter(value: Int, seqNr: SeqNr) {
    def apply(event: Event, seqNr: SeqNr): Counter = {
      val x = event match {
        case Event.Inc => 1
        case Event.Dec => -1
      }
      Counter(value + x, seqNr)
    }
  }

  sealed trait Cmd
  object Cmd {
    case object Inc extends Cmd
    case object Dec extends Cmd
    case object Get extends Cmd
    case object Stop extends Cmd
  }


  sealed trait Event
  object Event {
    case object Inc extends Event
    case object Dec extends Event
  }
}
