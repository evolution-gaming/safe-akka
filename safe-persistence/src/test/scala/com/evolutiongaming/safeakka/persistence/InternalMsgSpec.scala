package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.{ActorRef, Status}
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, Sender}
import com.evolutiongaming.safeakka.persistence.PersistentBehavior.Rcv
import org.scalatest.wordspec.AnyWordSpec

class InternalMsgSpec extends AnyWordSpec with ActorSpec {

  type Event = Int
  type State = Int

  "SafePersistentActor" should {

    "receive private class as msg" in new Scope {
      ref ! Cmd.Inc
      expectMsg(1)
      ref ! Cmd.IncInternal
      expectMsg(3)
    }
  }

  private trait Scope extends ActorScope {

    val persistenceId = UUID.randomUUID().toString

    def persistenceSetup(ctx: ActorCtx) = new PersistenceSetup[State, State, Cmd, Event] {

      val log = ActorLog.empty.prefixed(persistenceId)

      def persistenceId = Scope.this.persistenceId

      def journalId = None

      def snapshotId = None

      def onRecoveryStarted(
        offer: Option[SnapshotOffer[State]],
        journaller: Journaller,
        snapshotter: Snapshotter[State]) = new Recovering {

        def state = 0

        def eventHandler(state: State, event: Event, seqNr: SeqNr) = state + event

        def onCompleted(state: State, seqNr: SeqNr) = {

          case class Internal(sender: Sender)

          def onEvent(state: State, event: Event, sender: Sender): PersistentBehavior[Cmd, Event] = {
            val stateAfter = state + event

            val onPersisted = (_: SeqNr) => {
              sender.tell(stateAfter, ActorRef.noSender)
              behavior(stateAfter)
            }

            val onFailure = (failure: Throwable) => {
              sender.tell(Status.Failure(failure), ActorRef.noSender)
            }
            PersistentBehavior.persist(Nel(Record(event)), onPersisted, onFailure)
          }

          def behavior(state: State): PersistentBehavior[Cmd, Event] = {

            val onSignal: OnSignal[Cmd, Event] = (signal, _) => signal match {
              case signal: PersistenceSignal.System =>
                testActor.tell(signal, ActorRef.noSender)
                behavior(state)

              case PersistenceSignal.Cmd(cmd, sender) => cmd match {
                case Cmd.Inc         => onEvent(state, 1, sender)
                case Cmd.IncInternal =>
                  ctx.self.tell(Internal(sender), ActorRef.noSender)
                  behavior(state)
              }
            }

            val onAny: OnAny[Cmd, Event] = (_: SeqNr, _: Sender) => {
              case Internal(sender) => onEvent(state, 2, sender)
            }

            Rcv(onSignal, onAny)
          }

          behavior(state)
        }

        def onStopped(state: State, seqNr: SeqNr) = {}
      }

      def onStopped(seqNr: SeqNr) = {}
    }

    val ref = PersistentActorRef(persistenceSetup)

    sealed trait Cmd
    object Cmd {
      case object Inc extends Cmd
      case object IncInternal extends Cmd
    }
  }
}
