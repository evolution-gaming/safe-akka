package com.evolutiongaming.safeakka.persistence

import java.io.NotSerializableException
import java.util.UUID

import akka.actor.{ActorRef, PoisonPill, Status}
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, Signal}
import com.evolutiongaming.safeakka.persistence.TestSerializer.Msg
import com.evolutiongaming.safeakka.persistence.{PersistentBehavior => Behavior}
import org.scalatest.WordSpec

import scala.util.{Failure, Success}

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
      expectMsg(Success((1, 1l)))
      expectMsg((1, 1l) :: Nil)

      ref ! Cmd.Append(2)
      expectMsg(Success((2, 2l)))
      expectMsg((2, 2l) :: (1, 1l) :: Nil)

      ref ! Cmd.SaveSnapshot
      expectMsgType[PersistenceSignal.SaveSnapshotSuccess]

      ref ! PoisonPill
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))

      val ref2 = PersistentActorRef(persistenceSetup)
      ref2 ! PoisonPill
      expectMsg(SnapshotRecoveryStopped(0))
    }

    "stop while reading events" in new Scope {
      val events = (1 to 1000).toList
      ref ! Cmd.Append(events)
      expectMsg(RecoveryStarted(None))
      expectMsgAllOf(events.map(event => Success((event, event.toLong))): _*)
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
      expectMsg(Success((1, 1l)))
      expectMsg((1, 1l) :: Nil)
      ref ! Cmd.Stop
      expectMsg(Stopped)
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))
    }

    "handle NotSerializableException" in new FailureScope {
      val cmd = Cmd(Nel(
        Msg.Serializable,
        Msg.NotSerializable,
        Msg.Serializable))
      ref.tell(cmd, testActor)
      expectMsgPF() { case Failure(_: NotSerializableException) => true }
      expectMsgPF() { case Failure(_: NotSerializableException) => true }
      expectMsgPF() { case Failure(_: NotSerializableException) => true }
      expectMsgType[NotSerializableException]
      expectMsg(PersistenceSignal.Sys(Signal.PostStop))
    }

    "handle persistence failures" in new FailureScope {
      val cmd = Cmd(Nel(
        Msg.Serializable,
        Msg.Illegal,
        Msg.Serializable))
      ref.tell(cmd, testActor)
      expectMsgPF() { case Failure(_: IllegalArgumentException) => true }
      expectMsgPF() { case Failure(_: IllegalArgumentException) => true }
      expectMsgPF() { case Failure(_: IllegalArgumentException) => true }
      expectMsgType[IllegalArgumentException]
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

      def onRecoveryStarted(
        offer: Option[SnapshotOffer[State]],
        journaller: Journaller,
        snapshotter: Snapshotter[State]) = {

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
                    val (records, newState, _) = events.foldLeft(zero) { case ((records, state, seqNrPrev), event) =>
                      val record = Record.of(event) { seqNr =>
                        val msg = seqNr.map(seqNr => (event, seqNr))
                        sender.tell(msg, ActorRef.noSender)
                      }
                      val seqNr = seqNrPrev + 1
                      val newState = eventHandler(state, event, seqNr)
                      (record :: records, newState, seqNr)
                    }
                    (newState, records.reverse)
                  }

                  Nel.opt(records) match {
                    case None =>
                      sender.tell(newState, ActorRef.noSender)
                      behavior(newState)

                    case Some(records) =>
                      val onPersisted = (_: SeqNr) => {
                        sender.tell(newState, ActorRef.noSender)
                        behavior(newState)
                      }

                      val onFailure = (failure: Throwable) => {
                        sender.tell(Status.Failure(failure), ActorRef.noSender)
                      }
                      PersistentBehavior.persist(records, onPersisted, onFailure)
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

    case class RecoveryStarted(offer: Option[SnapshotOffer[State]])
    case class SnapshotRecoveryStopped(seqNr: SeqNr)
    case class EventsRecoveryStopped(state: State, seqNr: SeqNr)
    case object Stopped
  }

  private trait FailureScope extends ActorScope {

    type Event = TestSerializer.Msg
    type State = Unit
    case class Cmd(events: Nel[Event])


    def persistenceSetup(ctx: ActorCtx) = new PersistenceSetup[State, State, Cmd, Event] {

      val persistenceId = UUID.randomUUID().toString

      def journalId = None

      def snapshotId = None

      def log = ActorLog.empty

      def onRecoveryStarted(
        offer: Option[SnapshotOffer[State]],
        journaller: Journaller,
        snapshotter: Snapshotter[State]) = new Recovering {

        def state = ()

        def eventHandler(state: State, event: Event, seqNr: SeqNr) = ()

        def onCompleted(state: State, seqNr: SeqNr) = behavior(state)

        def onStopped(state: State, seqNr: SeqNr) = {}

        def behavior(state: State): Behavior[Cmd, Event] = Behavior[Cmd, Event] { (signal, seqNr) =>
          signal match {
            case signal: PersistenceSignal.System   =>
              testActor.tell(signal, ActorRef.noSender)
              behavior(state)
            case PersistenceSignal.Cmd(cmd, sender) =>
              val onPersisted = (seqNr: SeqNr) => {
                sender.tell(seqNr, ActorRef.noSender)
                behavior(state)
              }
              val onFailure = (failure: Throwable) => {
                sender.tell(failure, ActorRef.noSender)
              }

              val records = cmd.events map { event =>
                Record.of(event) { seqNr =>
                  sender.tell(seqNr, ActorRef.noSender)
                }
              }
              Behavior.persist(records, onPersisted, onFailure)
          }
        }
      }

      def onStopped(seqNr: SeqNr) = {}
    }

    val ref = PersistentActorRef(persistenceSetup)
  }
}