package com.evolutiongaming.safeakka.actor

trait SafeActor[T] extends RcvSystem {

  type OnStop = () => Unit

  val asT: Unapply[T]

  def setup: SetupActor[T]

  val (phase, log) = {
    val (behavior, log) = setup(ActorCtx(context))
    val phase = nextPhase(Behavior.Empty, behavior)
    (VarCover[Phase](phase), log)
  }

  def receive: Receive = rcvSystem orElse {
    case asT(msg) => onSignal(Signal.Msg(msg, sender()))
    case msg      => onAny(msg, sender())
  }

  def onSystem(signal: Signal.System) = onSignal(signal)

  private def onSignal(signal: Signal[T]): Unit = {

    log.debug(s"onSignal $signal")

    def unexpected(phase: Phase) = sys error s"unexpected signal $signal in phase $phase"

    inPhase {
      case Phase.Receiving(prev) =>
        val nextPhase = this.nextPhase(prev, prev.onSignal(signal))
        signal match {
          case Signal.PostStop => Phase.Final
          case _               => nextPhase
        }

      case phase: Phase.Stopping =>
        signal match {
          case Signal.PostStop => phase.onStop(); Phase.Final
          case _               => unexpected(phase)
        }
    }
  }

  private def onAny(msg: Any, sender: Sender) = {
    inPhase {
      case Phase.Receiving(prev) if prev.onAny.isDefinedAt(msg) =>
        log.debug(s"onAny $msg")
        nextPhase(prev, prev.onAny(msg)(sender))
      case phase                                                =>
        onUnhandled(msg, "receive")
        phase
    }
  }

  private def inPhase(pf: PartialFunction[Phase, Phase]): Unit = {
    phase map { phase =>
      if (pf isDefinedAt phase) pf(phase)
      else sys error s"invalid phase $phase"
    }
  }

  def nextPhase(prev: Behavior.Rcv[T], next: Behavior[T]): Phase = next match {
    case next: Behavior.Rcv[T] => Phase.Receiving(next)
    case Behavior.Same         => Phase.Receiving(prev)
    case Behavior.Stop         =>
      context stop self
      Phase.Stopping(() => prev.onSignal(Signal.PostStop))
  }


  sealed trait Phase extends Product {
    override def toString: String = productPrefix
  }

  object Phase {
    case class Receiving(behavior: Behavior.Rcv[T]) extends Phase
    case class Stopping(onStop: OnStop) extends Phase
    case object Final extends Phase
  }
}

object SafeActor {

  def apply[T](setup: SetupActor[T])(implicit asT: Unapply[T]): SafeActor[T] = new Impl(setup, asT)

  class Impl[T](val setup: SetupActor[T], val asT: Unapply[T]) extends SafeActor[T]
}