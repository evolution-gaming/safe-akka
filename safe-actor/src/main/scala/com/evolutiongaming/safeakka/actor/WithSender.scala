package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ActorRef}

final case class WithSender[+A](msg: A, sender: Option[Sender] = None) {
  def senderOrNot: ActorRef = sender getOrElse Actor.noSender

  def map[B](ab: A => B): WithSender[B] = copy(msg = ab(msg))
}

object WithSender {

  def apply[A](msg: A, sender: Sender): WithSender[A] = {
    WithSender(msg, Option(sender))
  }

  def apply[A](signal: Signal.Msg[A]): WithSender[A] = {
    WithSender(signal.msg, signal.sender)
  }
}