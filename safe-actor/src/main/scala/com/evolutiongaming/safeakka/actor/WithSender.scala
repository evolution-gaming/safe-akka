package com.evolutiongaming.safeakka.actor

import akka.actor.ActorRef

final case class WithSender[+A](msg: A, sender: Option[Sender] = None) {
  def senderOrNot: Sender = sender getOrElse Sender.Empty

  def map[B](ab: A => B): WithSender[B] = copy(msg = ab(msg))
}

object WithSender {

  def apply[A](msg: A, sender: Sender): WithSender[A] = {
    WithSender(msg, Some(sender))
  }

  def apply[A](msg: A, sender: ActorRef): WithSender[A] = {
    WithSender(msg, Some(Sender(sender)))
  }

  def apply[A](signal: Signal.Msg[A]): WithSender[A] = {
    WithSender(signal.msg, signal.sender)
  }
}