package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.ActorRef
import akka.persistence.SnapshotMetadata
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorLog, Signal}
import com.evolutiongaming.safeakka.persistence.{PersistentBehavior => Behavior}
import org.scalatest.WordSpec

class TestPersistentActorSpec extends WordSpec with ActorSpec {

  type Event = Int
  type State = WithNr[Int]

  "TestPersistentActor" should {

    "receive snapshot offer" in new Scope {
      val seqNr = 10L
      val metadata = SnapshotMetadata(persistenceId, sequenceNr = seqNr)
      val state = WithNr(0, seqNr = seqNr)

      ref.recover(Some(SnapshotOffer(metadata, state)))
      ref ! Cmd.Get
      expectMsg(state)
    }

    "stash commands while recovering" in new Scope {
      val seqNr = 10L
      val metadata = SnapshotMetadata(persistenceId, sequenceNr = seqNr)
      val state = WithNr(0, seqNr = seqNr)

      ref ! Cmd.Inc
      ref ! Cmd.Inc
      ref ! Cmd.Get
      ref ! Cmd.Inc
      ref.recover(Some(SnapshotOffer(metadata, state)))
      expectMsg(WithNr(value = 1, seqNr = 11))
      expectMsg(WithNr(value = 2, seqNr = 12))
      expectMsg(WithNr(value = 2, seqNr = 12))
      expectMsg(WithNr(value = 3, seqNr = 13))
    }

    "receive event" in new Scope {
      ref.recoverEvents(1, 2, 3)
      ref ! Cmd.Get
      expectMsg(WithNr(value = 6, seqNr = 3))
    }

    "receive command" in new Scope {
      ref.recover()
      ref ! Cmd.Get
      expectMsg(WithNr(value = 0, seqNr = 0))

      ref ! Cmd.Inc
      expectMsg(WithNr(value = 1, seqNr = 1))
    }

    "stop self" in new Scope {
      ref.recover()
      ref ! Cmd.Inc
      expectMsg(WithNr(1, 1))
      ref ! Cmd.Stop
      expectMsg(Stopped)
      expectMsg(Signal.PostStop)
    }
  }

  private trait Scope extends ActorScope {

    val eventHandler: EventHandler[State, Event] = (state, offer) => WithNr(state.value + offer.value, offer.seqNr)

    val persistenceId = UUID.randomUUID().toString

    def persistenceSetup(ctx: PersistentActorCtx[State]) = new PersistenceSetup[State, State, Cmd, Event] {

      def persistenceId = Scope.this.persistenceId

      def log = ActorLog.empty

      def onRecoveryStarted(snapshotOffer: Option[SnapshotOffer[State]]) = {
        val state: State = snapshotOffer map { _.snapshot } getOrElse WithNr(0, 0)
        Recovering(state, eventHandler)
      }

      def onRecoveryStopped(seqNr: SeqNr) = {}

      def onRecoveryCompleted(state: WithNr[State]): Behavior[Cmd, Event] = behavior(state.value)

      private def behavior(state: State): Behavior[Cmd, Event] = Behavior[Cmd, Event] {

        case signal: Signal.System =>
          testActor.tell(signal, ActorRef.noSender)
          behavior(state)

        case Signal.Msg(WithNr(signal, seqNr), sender) => signal match {
          case PersistenceSignal.Cmd(cmd) => cmd match {
            case Cmd.Inc =>
              val event = 1
              val stateAfter = eventHandler(state, WithNr(event, seqNr + 1))
              Behavior.persist(Record(event)) { _ =>
                sender.tell(stateAfter, ActorRef.noSender)
                behavior(stateAfter)
              }

            case Cmd.Get =>
              sender ! state
              behavior(state)

            case Cmd.Stop =>
              sender.tell(Stopped, ActorRef.noSender)
              Behavior.stop
          }

          case signal: PersistenceSignal.System =>
            testActor.tell(signal, ActorRef.noSender)
            behavior(state)
        }
      }
    }

    val ref = TestPersistentActorRef(persistenceSetup, Eventsourced.empty, Snapshotter.empty)

    sealed trait Cmd
    object Cmd {
      case object Inc extends Cmd
      case object Get extends Cmd
      case object Stop extends Cmd
    }

    case object Stopped
  }
}