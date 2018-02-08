package com.evolutiongaming.safeakka.persistence

import akka.{persistence => ap}
import com.evolutiongaming.safeakka.actor._

import scala.collection.immutable.Seq


trait SafePersistent[S, SS, C, E] extends RcvSystem {

  type OnStop = () => Unit

  val asS: Unapply[S]
  val asC: Unapply[C]
  val asE: Unapply[E]

  def setup: SetupPersistentActor[S, SS, C, E]

  def persistEvents(events: Seq[E])(handler: E => Unit): Unit

  def lastSeqNr: SeqNr

  def ctx: PersistentActorCtx[S]

  val (persistenceId, log, phase) = {
    val persistenceSetup = setup(ctx)
    val phase = Phase.ReadingSnapshot(
      persistenceSetup.onRecoveryStarted,
      persistenceSetup.onStopped)
    (persistenceSetup.persistenceId, persistenceSetup.log, VarCover[Phase](phase))
  }

  def rcvRecover: Receive = {
    case asE(event)                      => onEvent(WithNr(event, lastSeqNr))
    case ap.SnapshotOffer(m, asS(state)) => onSnapshotOffer(SnapshotOffer(m, state))
    case ap.RecoveryCompleted            => onRecoveryCompleted()
  }

  def rcvCommand: Receive = rcvSystem orElse {
    case asC(cmd)                    => onCmd(PersistenceSignal.Cmd(cmd), sender())
    case ap.SnapshotResponse(signal) => onCmd(signal, sender())
    case ap.EventsResponse(signal)   => onCmd(signal, sender())
    case msg                         => onAny(msg, sender())
  }

  def onSystem(signal: Signal.System): Unit = {
    log.debug(s"[$lastSeqNr] onSystem $signal")

    def unexpected(phase: Phase) = sys error s"$persistenceId unexpected signal $signal in phase $phase"

    inPhase {
      case phase: Phase.Receiving =>
        val nextPhase = this.nextPhase(phase, signal)
        signal match {
          case Signal.PostStop => Phase.Final
          case _               => nextPhase
        }

      case phase: Phase.ReadingEvents => signal match {
        case Signal.PostStop       => phase.onStop(WithNr(phase.state, lastSeqNr)); Phase.Final
        case _: Signal.PostRestart => phase
        case _                     => unexpected(phase)
      }

      case phase: Phase.ReadingSnapshot => signal match {
        case Signal.PostStop       => phase.onStop(lastSeqNr); Phase.Final
        case _: Signal.PostRestart => phase
        case _                     => unexpected(phase)
      }

      case phase: Phase.Stopping => signal match {
        case Signal.PostStop => phase.onStop(); Phase.Final
        case _               => unexpected(phase)
      }

      case phase: Phase.Persisting => signal match {
        case Signal.PostStop => phase.onStop(); Phase.Final
        case _               => unexpected(phase)
      }
    }
  }

  override def toString: String = s"SafePersistentActor($persistenceId)"

  private def inPhase(pf: PartialFunction[Phase, Phase]): Unit = {
    phase map { phase =>
      if (pf isDefinedAt phase) pf(phase)
      else sys error s"$persistenceId invalid phase $phase"
    }
  }

  protected def onSnapshotOffer(snapshot: SnapshotOffer[S]): Unit = {
    log.debug(s"[${ snapshot.metadata.sequenceNr }] onSnapshotOffer $snapshot")

    inPhase {
      case phase: Phase.ReadingSnapshot =>
        val recovering = phase.onRecoveryStarted(Some(snapshot))
        Phase.ReadingEvents(
          recovering.state,
          recovering.eventHandler,
          recovering.onCompleted,
          recovering.onStopped)
    }
  }

  protected def onEvent(event: WithNr[E]): Unit = {
    log.debug(s"[$lastSeqNr] onEvent ${ event.value }")

    inPhase {
      case phase: Phase.ReadingEvents =>
        val state = phase.eventHandler(phase.state, event)
        phase.copy(state = state)

      case phase: Phase.ReadingSnapshot =>
        val recovering = phase.onRecoveryStarted(None)
        val eventHandler = recovering.eventHandler _
        val state = eventHandler(recovering.state, event)
        Phase.ReadingEvents(state, eventHandler, recovering.onCompleted, recovering.onStopped)
    }
  }

  protected def onRecoveryCompleted(): Unit = {
    def apply(state: SS, onRecoveryCompleted: OnRecoveryCompleted[SS, C, E], onStop: OnStop) = {
      log.debug(s"[$lastSeqNr] onRecoveryCompleted $state")
      val behavior = onRecoveryCompleted(WithNr(state, lastSeqNr))
      nextPhase(behavior, onStop)
    }

    inPhase {
      case phase: Phase.ReadingEvents =>
        val onStop = () => phase.onStop(WithNr(phase.state, lastSeqNr))
        apply(phase.state, phase.onCompleted, onStop)

      case phase: Phase.ReadingSnapshot =>
        val recovering = phase.onRecoveryStarted(None)
        val onStop = () => phase.onStop(lastSeqNr)
        apply(recovering.state, recovering.onCompleted, onStop)
    }
  }


  protected def onCmd(signal: PersistenceSignal[C], sender: Sender): Unit = {

    def logError() = log.error(s"[$lastSeqNr] onCmd $signal")

    signal match {
      case _: PersistenceSignal.SaveSnapshotFailure    => logError()
      case _: PersistenceSignal.DeleteSnapshotFailure  => logError()
      case _: PersistenceSignal.DeleteSnapshotsFailure => logError()
      case _: PersistenceSignal.DeleteEventsFailure    => logError()
      case _                                           => log.debug(s"[$lastSeqNr] onCmd $signal")
    }

    inPhase {
      case phase: Phase.Receiving => nextPhase(phase, Signal.Msg(WithNr(signal, lastSeqNr), sender))
    }
  }

  protected def onAny(msg: Any, sender: Sender): Unit = {
    inPhase {
      case Phase.Receiving(behavior) if behavior.onAny.isDefinedAt(msg) =>
        
        def senderStr = if (sender == ctx.system.deadLetters) "" else s" $sender"
        log.debug(s"[$lastSeqNr] onAny $msg$senderStr")

        val next = behavior.onAny(msg)(lastSeqNr, sender)
        nextPhase(next, () => behavior.onSignal(Signal.PostStop))
      case phase                                                        =>
        onUnhandled(msg, s"[$lastSeqNr] onCmd")
        phase
    }
  }

  private def nextPhase(phase: Phase.Receiving, signal: PSignal[C]): Phase = {
    val behavior = phase.behavior
    nextPhase(behavior.onSignal(signal), () => behavior.onSignal(Signal.PostStop))
  }

  private def nextPhase(behavior: PersistentBehavior[C, E], onStop: OnStop): Phase = behavior match {
    case behavior: PersistentBehavior.Rcv[C, E] => Phase.Receiving(behavior)

    case PersistentBehavior.Persist(records, onPersisted) =>

      def nextPhase = this.nextPhase(onPersisted(lastSeqNr), onStop)

      if (records.isEmpty) nextPhase
      else {
        val events = records map { _.event }
        val iterator = records.toIterator
        persistEvents(events) { _ =>
          iterator.next.onPersisted(lastSeqNr)
          if (iterator.isEmpty) inPhase {
            case _: Phase.Persisting => nextPhase
          }
        }
        Phase.Persisting(onStop)
      }

    case PersistentBehavior.Stop =>
      context stop self
      Phase.Stopping(onStop)
  }


  sealed trait Phase extends Product {
    override def toString: String = productPrefix
  }

  object Phase {

    case class ReadingSnapshot(
      onRecoveryStarted: OnRecoveryStarted[S, SS, C, E],
      onStop: Callback[SeqNr]) extends Phase

    case class ReadingEvents(
      state: SS,
      eventHandler: EventHandler[SS, E],
      onCompleted: OnRecoveryCompleted[SS, C, E],
      onStop: Callback[WithNr[SS]]) extends Phase

    case class Receiving(behavior: PersistentBehavior.Rcv[C, E]) extends Phase

    case class Stopping(onStop: OnStop) extends Phase

    case class Persisting(onStop: OnStop) extends Phase

    case object Final extends Phase
  }
}

trait SafePersistentActor[S, SS, C, E] extends SafePersistent[S, SS, C, E] with ap.PersistentActor

object SafePersistentActor {

  def apply[S, SS, C, E](
    setup: SetupPersistentActor[S, SS, C, E],
    journalPluginId: Option[String] = None,
    snapshotPluginId: Option[String] = None
  )(implicit
    asS: Unapply[S],
    asC: Unapply[C],
    asE: Unapply[E]): SafePersistentActor[S, SS, C, E] = {

    new Impl(setup, journalPluginId, snapshotPluginId, asS, asC, asE)
  }

  private class Impl[S, SS, C, E](
    val setup: SetupPersistentActor[S, SS, C, E],
    journalPlgnId: Option[String],
    snapshotPlgnId: Option[String],
    val asS: Unapply[S],
    val asC: Unapply[C],
    val asE: Unapply[E]) extends SafePersistentActor[S, SS, C, E] {

    override def journalPluginId: String = journalPlgnId.getOrElse(super.journalPluginId)

    override def snapshotPluginId: String = snapshotPlgnId.getOrElse(super.snapshotPluginId)

    def ctx = PersistentActorCtx(this)

    def persistEvents(events: Seq[E])(handler: E => Unit) = persistAll(events)(handler)

    def lastSeqNr = lastSequenceNr

    def receiveRecover = rcvRecover orElse {
      case msg => onUnhandled(msg, "receiveRecover")
    }

    def receiveCommand = rcvCommand

  }
}