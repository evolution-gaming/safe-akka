package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.ActorRef
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorLog, Signal}
import com.evolutiongaming.safeakka.persistence.{PersistentBehavior => Behavior}
import org.scalatest.WordSpec

import scala.concurrent.duration._

class CounterSpec extends WordSpec with ActorSpec {
  import CounterSpec._

  "Counter" should {

    "handle commands and persist events" in new Scope {
      ref ! Cmd.Get
      expectMsg(Counter(0, 0))

      expectMsg(Signal.RcvTimeout)

      ref ! Cmd.Inc
      expectMsg(Event.Inc)
      expectMsg(Counter(1, 1))

      ref ! Cmd.Dec
      expectMsg(Event.Dec)
      expectMsg(Counter(0, 2))
      expectMsgType[PersistenceSignal.SaveSnapshotSuccess]

      ref ! Cmd.Stop
      expectMsg(Signal.PostStop)
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

    val eventHandler: EventHandler[Counter, Event] = (state, event) => {
      state(event.value, event.seqNr)
    }

    def persistenceSetup(ctx: PersistentActorCtx[Counter]) = {

      ctx.setReceiveTimeout(300.millis)

      new PersistenceSetup[Counter, Counter, Cmd, Event] {
        val persistenceId = UUID.randomUUID().toString

        val log = ActorLog.empty

        def onRecoveryStarted(snapshotOffer: Option[SnapshotOffer[Counter]]) = new Recovering {

          def state = snapshotOffer map { _.snapshot } getOrElse Counter(0, 0)

          def eventHandler(state: Counter, offer: WithNr[Event]) = state(offer.value, offer.seqNr)

          def onCompleted(state: WithNr[Counter]) = behavior(state.value)

          def onStopped(state: WithNr[Counter]) = {}
        }

        def onStopped(seqNr: SeqNr): Unit = {}

        private def behavior(counter: Counter): Behavior[Cmd, Event] = Behavior[Cmd, Event] {
          case signal: Signal.System =>
            testActor.tell(signal, ActorRef.noSender)
            if (signal == Signal.RcvTimeout) ctx.setReceiveTimeout(Duration.Inf)
            behavior(counter)

          case Signal.Msg(signal, sender) => signal.value match {
            case PersistenceSignal.Cmd(cmd) =>

              def onEvent(event: Event) = {

                val record = Record.of(event)(_ => sender.tell(event, ActorRef.noSender))
                Behavior.persist(record) { seqNr =>
                  val newCounter = counter(event, seqNr)
                  sender.tell(newCounter, ActorRef.noSender)
                  if (cmd == Cmd.Dec) ctx.snapshotter.save(newCounter)
                  behavior(newCounter)
                }
              }

              cmd match {
                case Cmd.Inc  => onEvent(Event.Inc)
                case Cmd.Dec  => onEvent(Event.Dec)
                case Cmd.Stop => Behavior.stop
                case Cmd.Get  =>
                  sender.tell(counter, ActorRef.noSender)
                  behavior(counter)
              }

            case signal: PersistenceSignal.System =>
              testActor.tell(signal, ActorRef.noSender)
              behavior(counter)
          }
        }
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
