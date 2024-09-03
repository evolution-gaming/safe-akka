package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.{ActorRef, Status}
import akka.persistence.SnapshotMetadata
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, Signal}
import com.evolutiongaming.safeakka.persistence.PersistentBehavior as Behavior
import org.scalatest.wordspec.AnyWordSpec

class TestPersistentActorSpec extends AnyWordSpec with ActorSpec {

  type Event = Int
  type State = (Int, SeqNr)

  "TestPersistentActor" should {

    "receive snapshot offer" in new Scope {
      val seqNr = 10L
      val metadata = SnapshotMetadata(persistenceId, sequenceNr = seqNr)
      val state = (0, seqNr)

      ref.recover(Some(SnapshotOffer(metadata, state)))
      ref ! Cmd.Get
      expectMsg(state)
    }

    "stash commands while recovering" in new Scope {
      val seqNr = 10L
      val metadata = SnapshotMetadata(persistenceId, sequenceNr = seqNr)
      val state = (0, seqNr)

      ref ! Cmd.Inc
      ref ! Cmd.Inc
      ref ! Cmd.Get
      ref ! Cmd.Inc
      ref.recover(Some(SnapshotOffer(metadata, state)))
      expectMsg((1, 11))
      expectMsg((2, 12))
      expectMsg((2, 12))
      expectMsg((3, 13))
    }

    "receive event" in new Scope {
      ref.recoverEvents(1, 2, 3)
      ref ! Cmd.Get
      expectMsg((6, 3))
    }

    "receive command" in new Scope {
      ref.recover()
      ref ! Cmd.Get
      expectMsg((0, 0))

      ref ! Cmd.Inc
      expectMsg((1, 1))
    }

    "stop self" in new Scope {
      ref.recover()
      ref ! Cmd.Inc
      expectMsg((1, 1))
      ref ! Cmd.Stop
      expectMsg(Stopped)
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))
    }
  }

  private trait Scope extends ActorScope {

    val eventHandler: EventHandler[State, Event] = { case ((state, _), event, seqNr) => (state + event, seqNr) }

    val persistenceId: String = UUID.randomUUID().toString

    private val persistenceSetup = (_: ActorCtx) => new PersistenceSetup[State, State, Cmd, Event] {

      override def persistenceId: String = Scope.this.persistenceId

      override def journalId: Option[String] = None

      override def snapshotId: Option[String] = None

      override def log: ActorLog = ActorLog.empty.prefixed(persistenceId)

      override def onRecoveryStarted(
        offer: Option[SnapshotOffer[(Event, SeqNr)]],
        journaller: Journaller,
        snapshotter: Snapshotter[(Event, SeqNr)]): Recovering = new Recovering {

        override def state: (Event, SeqNr) = offer.map { _.snapshot }.getOrElse((0, 0))

        override def eventHandler(state: State, event: Event, seqNr: SeqNr): (Event, SeqNr) = (state._1 + event, seqNr)

        override def onCompleted(state: State, seqNr: SeqNr): Behavior[Cmd, Event] = behavior(state)

        override def onStopped(state: State, seqNr: SeqNr): Unit = {}

        private def behavior(state: State): Behavior[Cmd, Event] = Behavior[Cmd, Event] { (signal, seqNr) =>
          signal match {
            case signal: PersistenceSignal.System =>
              testActor.tell(signal, ActorRef.noSender)
              behavior(state)

            case PersistenceSignal.Cmd(cmd, sender) => cmd match {
              case Cmd.Inc =>
                val event = 1
                val stateAfter = eventHandler(state, event, seqNr + 1)

                val onPersisted = (_: SeqNr) => {
                  sender.tell(stateAfter, ActorRef.noSender)
                  behavior(stateAfter)
                }

                val onFailure = (failure: Throwable) => {
                  sender.tell(Status.Failure(failure), ActorRef.noSender)
                }
                PersistentBehavior.persist(Nel(Record(event)), onPersisted, onFailure)

              case Cmd.Get =>
                sender ! state
                behavior(state)

              case Cmd.Stop =>
                sender.tell(Stopped, ActorRef.noSender)
                Behavior.stop
            }
          }
        }
      }

      override def onStopped(seqNr: SeqNr): Unit = {}
    }

    val ref: TestPersistentActorRef[(Event, SeqNr), Cmd, Event] = TestPersistentActorRef(persistenceSetup, Journaller.empty, Snapshotter.empty)

    sealed trait Cmd
    object Cmd {
      case object Inc extends Cmd
      case object Get extends Cmd
      case object Stop extends Cmd
    }

    case object Stopped
  }
}