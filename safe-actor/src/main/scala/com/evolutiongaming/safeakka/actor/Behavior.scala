package com.evolutiongaming.safeakka.actor

sealed trait Behavior[-T]

object Behavior {

  private[safeakka] val Empty: Rcv[Any] = Rcv { (_: Any) => same }


  def same[T]: Behavior[T] = Same

  def stop[T]: Behavior[T] = Stop

  def apply[T](onSignal: OnSignal[T]): Behavior[T] = Rcv(onSignal)

  def empty[T]: Behavior[T] = Empty

  def onMsg[T](onMsg: Signal.Msg[T] => Behavior[T]): Behavior[T] = Behavior[T] {
    case signal: Signal.Msg[T] => onMsg(signal)
    case _: Signal.System      => same
  }

  def stateless[T](onMsg: Signal[T] => Unit): Behavior[T] = Behavior[T] { signal =>
    onMsg(signal)
    Behavior.same
  }

  def statelessOnMsg[T](onMsg: Signal.Msg[T] => Unit): Behavior[T] = stateless[T] {
    case signal: Signal.Msg[T] => onMsg(signal)
    case _                     => ()
  }

  case class Rcv[-T](
    onSignal: OnSignal[T],
    onAny: OnAny[T] = PartialFunction.empty) extends Behavior[T]

  sealed trait NonRcv extends Behavior[Any]

  case object Stop extends NonRcv
  case object Same extends NonRcv
}
