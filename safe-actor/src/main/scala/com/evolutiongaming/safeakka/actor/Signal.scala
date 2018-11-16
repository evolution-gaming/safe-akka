package com.evolutiongaming.safeakka.actor

import akka.actor.ActorRef

sealed trait Signal[+A] {
  def map[B](ab: A => B): Signal[B]
}

object Signal {

  final case class Msg[A](msg: A, sender: Sender) extends Signal[A] {
    def map[B](ab: A => B): Msg[B] = copy(msg = ab(msg))
  }

  object Msg {
    def apply[A](msg: A, sender: ActorRef): Msg[A] = Msg(msg, Sender(sender))
  }

  sealed trait System extends Signal[Nothing] { self =>
    def map[B](f: Nothing => B): System = self
  }

  case object RcvTimeout extends System
  final case class Terminated(ref: ActorRef) extends System

  sealed trait NonRcv extends System
  case object PostStop extends NonRcv
  final case class PostRestart(reason: Throwable) extends NonRcv
}