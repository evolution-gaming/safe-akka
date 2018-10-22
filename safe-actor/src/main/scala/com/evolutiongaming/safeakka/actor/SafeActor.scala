package com.evolutiongaming.safeakka.actor

trait SafeActor[A] extends RcvSystem {

  type OnStop = () => Unit

  val asT: Unapply[A]

  def setup: SetupActor[A]

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

  private def onSignal(signal: Signal[A]): Unit = {

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

  def nextPhase(prev: Behavior.Rcv[A], next: Behavior[A]): Phase = next match {
    case next: Behavior.Rcv[A] => Phase.Receiving(next)
    case Behavior.Same         => Phase.Receiving(prev)
    case Behavior.Stop         =>
      context stop self
      Phase.Stopping(() => prev.onSignal(Signal.PostStop))
  }


  sealed trait Phase extends Product {
    override def toString: String = productPrefix
  }

  object Phase {
    case class Receiving(behavior: Behavior.Rcv[A]) extends Phase
    case class Stopping(onStop: OnStop) extends Phase
    case object Final extends Phase
  }
}

object SafeActor {

  def apply[A](setup: SetupActor[A])(implicit asT: Unapply[A]): SafeActor[A] = new Impl(setup, asT)

  class Impl[A](val setup: SetupActor[A], val asT: Unapply[A]) extends SafeActor[A]
}