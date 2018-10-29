package com.evolutiongaming.safeakka.actor

import akka.actor.Actor


trait SafeActor[A] extends Actor

object SafeActor {

  def apply[A](setup: SetupActor[A])(implicit unapply: Unapply[A]): SafeActor[A] = new SafeActor[A] {

    val (phase, log) = {
      val ctx = ActorCtx(context)
      val (behavior, log) = setup(ctx)
      val phase = behavior match {
        case behavior: Behavior.Rcv[A] => Phase.Receiving(behavior)
        case Behavior.Same             => sys.error("invalid Behavior.Same")
        case Behavior.Stop             =>
          context.stop(self)
          Phase.Stopping(() => ())
      }
      (VarCover[Phase](phase), log)
    }

    def receive: Receive = RcvSystem(onSignal) orElse {
      case unapply(msg) => onSignal(Signal.Msg(msg, sender()))
      case msg          => onAny(msg, sender())
    }

    override def postStop(): Unit = {
      onSignal(Signal.PostStop)
      super.postStop()
    }

    override def postRestart(reason: Throwable): Unit = {
      super.postRestart(reason)
      onSignal(Signal.PostRestart(reason))
    }

    def onSignal(signal: Signal[A]): Unit = {

      log.debug(s"onSignal $signal")

      def unexpected(phase: Phase) = sys error s"unexpected signal $signal in phase $phase"

      phase map {
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

        case phase => invalid(phase)
      }
    }

    def onAny(msg: Any, sender: Sender): Unit = {
      phase map {
        case Phase.Receiving(prev) if prev.onAny.isDefinedAt(msg) =>
          log.debug(s"onAny $msg")
          nextPhase(prev, prev.onAny(msg)(sender))

        case phase =>
          log.warn {
            if (sender == context.system.deadLetters) {
              s"receive unhandled message: ${ msg.getClass.getName }"
            } else {
              s"receive unhandled message: ${ msg.getClass.getName }, sender: $sender"
            }
          }
          unhandled(msg)
          phase
      }
    }

    def nextPhase(prev: Behavior.Rcv[A], next: Behavior[A]): Phase = next match {
      case next: Behavior.Rcv[A] => Phase.Receiving(next)
      case Behavior.Same         => Phase.Receiving(prev)
      case Behavior.Stop         =>
        context stop self
        Phase.Stopping(() => prev.onSignal(Signal.PostStop))
    }

    def invalid(phase: Phase): Nothing = sys.error(s"invalid phase $phase")

    sealed trait Phase extends Product {
      override def toString: String = productPrefix
    }

    object Phase {
      case class Receiving(behavior: Behavior.Rcv[A]) extends Phase
      case class Stopping(onStop: () => Unit) extends Phase
      case object Final extends Phase
    }
  }
}