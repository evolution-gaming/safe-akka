package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ActorRef}

case class WithSender[+T](msg: T, sender: Option[ActorRef] = None) {
  def senderOrNot: ActorRef = sender getOrElse Actor.noSender
}

object WithSender {

  def apply[T](msg: T, sender: ActorRef): WithSender[T] = {
    WithSender(msg, Option(sender))
  }

  def apply[T](signal: Signal.Msg[T]): WithSender[T] = {
    WithSender(signal.msg, signal.sender)
  }
}