package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.{ActorRef, PoisonPill}
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorLog, Signal}
import com.evolutiongaming.safeakka.persistence.{PersistentBehavior => Behavior}
import org.scalatest.WordSpec

class SafePersistentActorSpec extends WordSpec with ActorSpec {

  type Event = Int
  type State = List[WithNr[Int]]

  "SafePersistentActor" should {

    "support persisting zero events" in new Scope {
      ref ! Cmd.Append(Nil)
      expectMsg(RecoveryStarted(None))
      expectMsg(Nil)
    }

    "stop while reading snapshot" in new Scope {
      ref ! Cmd.Append(1)
      expectMsg(RecoveryStarted(None))
      expectMsg(WithNr(1, 0) :: Nil)

      ref ! Cmd.Append(2)
      expectMsg(WithNr(2, 1) :: WithNr(1, 0) :: Nil)

      ref ! Cmd.SaveSnapshot
      expectMsgType[PersistenceSignal.SaveSnapshotSuccess]

      ref ! PoisonPill
      expectMsg(Signal.PostStop)

      val ref2 = PersistentActorRef(persistenceSetup)
      ref2 ! PoisonPill
      expectMsg(SnapshotRecoveryStopped(0))
    }

    "stop while reading events" in new Scope {
      ref ! Cmd.Append((0 to 1000).toList)
      expectMsg(RecoveryStarted(None))
      expectMsgType[State]

      ref ! PoisonPill
      expectMsg(Signal.PostStop)


      val ref2 = PersistentActorRef(persistenceSetup)
      expectMsg(RecoveryStarted(None))
      ref2 ! PoisonPill

      expectMsgType[EventsRecoveryStopped]
    }

    "stop self" in new Scope {
      ref ! Cmd.Append(1)
      expectMsg(RecoveryStarted(None))
      expectMsg(WithNr(1, 0) :: Nil)
      ref ! Cmd.Stop
      expectMsg(Stopped)
      expectMsg(Signal.PostStop)
    }
  }

  private trait Scope extends ActorScope {

    val eventHandler: EventHandler[State, Event] = (state, eventOffer) => eventOffer :: state

    val persistenceId = UUID.randomUUID().toString

    def persistenceSetup(ctx: PersistentActorCtx[State]) = new PersistenceSetup[State, State, Cmd, Event] {

      def persistenceId = Scope.this.persistenceId

      def log = ActorLog.empty

      def onRecoveryStarted(snapshotOffer: Option[SnapshotOffer[State]]) = {
        testActor.tell(RecoveryStarted(snapshotOffer), ActorRef.noSender)

        val state: State = snapshotOffer map { _.snapshot } getOrElse Nil

        def onStop(state: WithNr[State]) = testActor ! EventsRecoveryStopped(state)

        Recovering(state, eventHandler, onRecoveryCompleted, onStop)
      }

      def onStopped(seqNr: SeqNr) = testActor ! SnapshotRecoveryStopped(seqNr)

      def onRecoveryCompleted(state: WithNr[State]): Behavior[Cmd, Event] = behavior(state.value)

      private def behavior(state: State): Behavior[Cmd, Event] = Behavior[Cmd, Event] {

        case signal: Signal.System =>
          testActor.tell(signal, ActorRef.noSender)
          behavior(state)

        case Signal.Msg(signal, sender) => signal.value match {
          case PersistenceSignal.Cmd(cmd) => cmd match {
            case Cmd.Append(events) =>

              val (newState, records) = {
                val zero = (List.empty[Record[Event]], state, signal.seqNr)
                val (records, newState, _) = events.foldLeft(zero) { case ((records, state, seqNr), event) =>
                  val record = Record(event)
                  val newState = eventHandler(state, WithNr(event, seqNr))
                  (record :: records, newState, seqNr + 1)
                }
                (newState, records.reverse)
              }

              Behavior.persist(records) { _ =>
                sender.tell(newState, ActorRef.noSender)
                behavior(newState)
              }

            case Cmd.SaveSnapshot =>
              ctx.snapshotter.save(state)
              behavior(state)

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
    case class EventsRecoveryStopped(state: WithNr[State])
    case object Stopped
  }
}