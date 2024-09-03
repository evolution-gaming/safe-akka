package com.evolutiongaming.safeakka.persistence

import java.util.UUID
import akka.actor.Status
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, SafeActorRef, Signal}
import com.evolutiongaming.safeakka.persistence.PersistentBehavior as Behavior

import scala.concurrent.duration.*
import org.scalatest.wordspec.AnyWordSpec

class CounterSpec extends AnyWordSpec with ActorSpec {
  import CounterSpec.*

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

    private def persistenceSetup(ctx: ActorCtx) = {

      ctx.setReceiveTimeout(300.millis)

      new PersistenceSetup[Counter, Counter, Cmd, Event] {

        override val persistenceId: String = UUID.randomUUID().toString

        override val log: ActorLog = ActorLog(system, classOf[CounterSpec]).prefixed(persistenceId)

        override def journalId: Option[String] = None

        override def snapshotId: Option[String] = None

        override def onRecoveryStarted(
          offer: Option[SnapshotOffer[Counter]],
          journaller: Journaller,
          snapshotter: Snapshotter[Counter]): Recovering = new Recovering {

          override def state: Counter = offer map { _.snapshot } getOrElse Counter(0, 0)

          override def eventHandler(state: Counter, event: Event, seqNr: SeqNr): Counter = state(event, seqNr)

          override def onCompleted(state: Counter, seqNr: SeqNr): Behavior[Cmd, Event] = {

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

          override def onStopped(state: Counter, seqNr: SeqNr): Unit = {}
        }

        override def onStopped(seqNr: SeqNr): Unit = {}
      }
    }

    val ref: SafeActorRef[Cmd] = PersistentActorRef(persistenceSetup)
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
