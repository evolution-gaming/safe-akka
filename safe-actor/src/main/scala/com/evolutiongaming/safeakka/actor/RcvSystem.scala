package com.evolutiongaming.safeakka.actor

import akka.actor.Actor.Receive
import akka.actor.{ReceiveTimeout, Terminated}

object RcvSystem {

  def apply(onSystem: Signal.System => Unit): Receive = {
    case ReceiveTimeout            => onSystem(Signal.RcvTimeout)
    case Terminated(ref)           => onSystem(Signal.Terminated(ref))
    case signal: Signal.Terminated => onSystem(signal)
  }
}