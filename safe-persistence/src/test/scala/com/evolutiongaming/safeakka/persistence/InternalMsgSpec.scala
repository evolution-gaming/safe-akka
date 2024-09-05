package com.evolutiongaming.safeakka.persistence

import java.util.UUID
import akka.actor.{ActorRef, Status}
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, SafeActorRef, Sender}
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

    private val persistenceId = UUID.randomUUID().toString

    private def persistenceSetup(ctx: ActorCtx) = new PersistenceSetup[State, State, Cmd, Event] {

      override val log: ActorLog = ActorLog.empty.prefixed(persistenceId)

      override def persistenceId: String = Scope.this.persistenceId

      override def journalId: Option[String] = None

      override def snapshotId: Option[String] = None

      override def onRecoveryStarted(
        offer: Option[SnapshotOffer[State]],
        journaller: Journaller,
        snapshotter: Snapshotter[State]): Recovering = new Recovering {

        override def state = 0

        override def eventHandler(state: State, event: Event, seqNr: SeqNr): State = state + event

        override def onCompleted(state: State, seqNr: SeqNr): PersistentBehavior[Cmd, Event] = {

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

        override def onStopped(state: State, seqNr: SeqNr): Unit = {}
      }

      override def onStopped(seqNr: SeqNr): Unit = {}
    }

    val ref: SafeActorRef[Cmd] = PersistentActorRef(persistenceSetup)

    sealed trait Cmd
    object Cmd {
      case object Inc extends Cmd
      case object IncInternal extends Cmd
    }
  }
}
