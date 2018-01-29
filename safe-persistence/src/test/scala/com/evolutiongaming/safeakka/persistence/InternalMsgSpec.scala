package com.evolutiongaming.safeakka.persistence

import java.util.UUID

import akka.actor.ActorRef
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorLog, Sender, Signal}
import com.evolutiongaming.safeakka.persistence.PersistentBehavior.Rcv
import org.scalatest.WordSpec

class InternalMsgSpec extends WordSpec with ActorSpec {

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

    def persistenceSetup(ctx: PersistentActorCtx[State]) = new PersistenceSetup[State, State, Cmd, Event] {

      val log = ActorLog.empty

      def persistenceId = Scope.this.persistenceId

      def onRecoveryStarted(snapshotOffer: Option[SnapshotOffer[State]]) = {
        val eventHandler: EventHandler[State, Event] = (state, eventOffer) => state + eventOffer.value
        Recovering(0, eventHandler, onRecoveryCompleted)
      }

      def onStopped(seqNr: SeqNr) = {}

      def onRecoveryCompleted(state: WithNr[State]) = {

        case class Internal(sender: Sender)

        def onEvent(state: State, event: Event, sender: Sender): PersistentBehavior[Cmd, Event] = {
          val stateAfter = state + event
          PersistentBehavior.persist(Record(event)) { _ =>
            sender.tell(stateAfter, ActorRef.noSender)
            behavior(stateAfter)
          }
        }

        def behavior(state: State): PersistentBehavior[Cmd, Event] = {

          val onSignal: OnSignal[Cmd, Event] = {
            case signal: Signal.System =>
              testActor.tell(signal, ActorRef.noSender)
              behavior(state)

            case Signal.Msg(signal, sender) => signal.value match {
              case PersistenceSignal.Cmd(cmd) => cmd match {
                case Cmd.Inc         => onEvent(state, 1, sender)
                case Cmd.IncInternal =>
                  ctx.self.tell(Internal(sender), ActorRef.noSender)
                  behavior(state)
              }

              case signal: PersistenceSignal.System =>
                testActor.tell(signal, ActorRef.noSender)
                behavior(state)
            }
          }

          val onAny: OnAny[Cmd, Event] = {
            case Internal(sender) => (_: SeqNr, _: Sender) => onEvent(state, 2, sender)
          }

          Rcv(onSignal, onAny)
        }

        behavior(state.value)
      }
    }

    val ref = PersistentActorRef(persistenceSetup)

    sealed trait Cmd
    object Cmd {
      case object Inc extends Cmd
      case object IncInternal extends Cmd
    }
  }
}
