package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.{ActorRef, PoisonPill}
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, Signal}
import com.evolutiongaming.safeakka.persistence.{PersistentBehavior => Behavior}
import org.scalatest.WordSpec

class SafePersistentActorSpec extends WordSpec with ActorSpec {

  type Event = Int
  type State = List[(Int, SeqNr)]

  "SafePersistentActor" should {

    "support persisting zero events" in new Scope {
      ref ! Cmd.Append(Nil)
      expectMsg(RecoveryStarted(None))
      expectMsg(Nil)
    }

    "stop while reading snapshot" in new Scope {
      ref ! Cmd.Append(1)
      expectMsg(RecoveryStarted(None))
      expectMsg((1, 0l) :: Nil)

      ref ! Cmd.Append(2)
      expectMsg((2, 1) :: (1, 0) :: Nil)

      ref ! Cmd.SaveSnapshot
      expectMsgType[PersistenceSignal.SaveSnapshotSuccess]

      ref ! PoisonPill
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))

      val ref2 = PersistentActorRef(persistenceSetup)
      ref2 ! PoisonPill
      expectMsg(SnapshotRecoveryStopped(0))
    }

    "stop while reading events" in new Scope {
      ref ! Cmd.Append((0 to 1000).toList)
      expectMsg(RecoveryStarted(None))
      expectMsgType[State]

      ref ! PoisonPill
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))


      val ref2 = PersistentActorRef(persistenceSetup)
      expectMsg(RecoveryStarted(None))
      ref2 ! PoisonPill

      expectMsgType[EventsRecoveryStopped]
    }

    "stop self" in new Scope {
      ref ! Cmd.Append(1)
      expectMsg(RecoveryStarted(None))
      expectMsg((1, 0) :: Nil)
      ref ! Cmd.Stop
      expectMsg(Stopped)
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))
    }
  }

  private trait Scope extends ActorScope {

    val persistenceId = UUID.randomUUID().toString

    def persistenceSetup(ctx: ActorCtx) = new PersistenceSetup[State, State, Cmd, Event] {

      def persistenceId = Scope.this.persistenceId

      def journalId = None

      def snapshotId = None

      def log = ActorLog.empty

      def onRecoveryStarted(offer: Option[SnapshotOffer[State]], journal: Journaller, snapshotter: Snapshotter[State]) = {

        testActor.tell(RecoveryStarted(offer), ActorRef.noSender)

        new Recovering {

          def state = offer map { _.snapshot } getOrElse Nil

          def eventHandler(state: State, event: Event, seqNr: SeqNr) = {
            (event, seqNr) :: state
          }

          def onCompleted(state: State, seqNr: SeqNr) = behavior(state)

          def onStopped(state: State, seqNr: SeqNr) = {
            testActor ! EventsRecoveryStopped(state, seqNr)
          }

          def behavior(state: State): Behavior[Cmd, Event] = Behavior[Cmd, Event] { (signal, seqNr) =>
            signal match {
              case signal: PersistenceSignal.System =>
                testActor.tell(signal, ActorRef.noSender)
                behavior(state)

              case PersistenceSignal.Cmd(cmd, sender) => cmd match {
                case Cmd.Append(events) =>

                  val (newState, records) = {
                    val zero = (List.empty[Record[Event]], state, seqNr)
                    val (records, newState, _) = events.foldLeft(zero) { case ((records, state, seqNr), event) =>
                      val record = Record(event)
                      val newState = eventHandler(state, event, seqNr)
                      (record :: records, newState, seqNr + 1)
                    }
                    (newState, records.reverse)
                  }

                  Behavior.persist(records) { _ =>
                    sender.tell(newState, ActorRef.noSender)
                    behavior(newState)
                  }

                case Cmd.SaveSnapshot =>
                  snapshotter.save(state)
                  behavior(state)

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
      }

      def onStopped(seqNr: SeqNr) = testActor ! SnapshotRecoveryStopped(seqNr)
    }

    val ref = PersistentActorRef(persistenceSetup)

    sealed trait Cmd
    object Cmd {
      case class Append(xs: List[Int]) extends Cmd
      object Append {
        def apply(x: Int, xs: Int*): Append = Append(x :: xs.toList)
      }
      case object SaveSnapshot extends Cmd
      case object Get extends Cmd
      case object Stop extends Cmd
    }

    case class RecoveryStarted(snapshotOffer: Option[SnapshotOffer[State]])
    case class SnapshotRecoveryStopped(seqNr: SeqNr)
    case class EventsRecoveryStopped(state: State, seqNr: SeqNr)
    case object Stopped
  }
}