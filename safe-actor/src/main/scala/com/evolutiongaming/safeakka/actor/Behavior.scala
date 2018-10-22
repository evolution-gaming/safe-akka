package com.evolutiongaming.safeakka.actor

sealed trait Behavior[-A] {
  def map[B](ba: B => A): Behavior[B]
}

object Behavior {

  private[safeakka] val Empty: Rcv[Any] = Rcv { _: Any => same }


  def same[A]: Behavior[A] = Same

  def stop[A]: Behavior[A] = Stop

  def apply[A](onSignal: OnSignal[A]): Behavior[A] = Rcv(onSignal)

  def empty[A]: Behavior[A] = Empty

  def onMsg[A](onMsg: Signal.Msg[A] => Behavior[A]): Behavior[A] = Behavior[A] {
    case signal: Signal.Msg[A] => onMsg(signal)
    case _: Signal.System      => same
  }

  def stateless[A](onMsg: Signal[A] => Unit): Behavior[A] = Behavior[A] { signal =>
    onMsg(signal)
    Behavior.same
  }

  def statelessOnMsg[A](onMsg: Signal.Msg[A] => Unit): Behavior[A] = stateless[A] {
    case signal: Signal.Msg[A] => onMsg(signal)
    case _                     => ()
  }

  final case class Rcv[-A](
    onSignal: OnSignal[A],
    onAny: OnAny[A] = PartialFunction.empty) extends Behavior[A] { self =>

    def map[B](ba: B => A): Rcv[B] = {
      val onSignal = (signal: Signal[B]) => self.onSignal(signal.map(ba)).map(ba)
      val onAny = self.onAny.andThen(_.andThen(_.map(ba)))
      copy(onSignal, onAny)
    }
  }

  sealed trait NonRcv extends Behavior[Any] { self =>
    def map[B](ba: B => Any): NonRcv = self
  }

  case object Stop extends NonRcv
  case object Same extends NonRcv
}
