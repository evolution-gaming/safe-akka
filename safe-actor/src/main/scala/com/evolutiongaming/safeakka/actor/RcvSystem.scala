package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ReceiveTimeout, Terminated}

trait RcvSystem extends Actor {

  def log: ActorLog

  def onSystem(signal: Signal.System): Unit

  override def postStop(): Unit = {
    onSystem(Signal.PostStop)
    super.postStop()
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    onSystem(Signal.PostRestart(reason))
  }

  private[safeakka] def rcvSystem: Receive = {
    case ReceiveTimeout            => onSystem(Signal.RcvTimeout)
    case Terminated(ref)           => onSystem(Signal.Terminated(ref))
    case signal: Signal.Terminated => onSystem(signal)
  }

  def onUnhandled(msg: Any, name: => String): Unit = {
    def warning = {
      if (sender() == context.system.deadLetters) {
        s"$name unhandled message ${ msg.getClass.getName }"
      } else {
        s"$name unhandled message ${ msg.getClass.getName } from ${ sender() }"
      }
    }
    log.warn(warning)
    unhandled(msg)
  }
}