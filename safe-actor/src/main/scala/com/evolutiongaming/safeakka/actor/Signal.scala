package com.evolutiongaming.safeakka.actor

import akka.actor.ActorRef

sealed trait Signal[+T]

object Signal {

  final case class Msg[T](msg: T, sender: Sender) extends Signal[T] {
    require(sender != null, "sender is null")
  }

  sealed trait System extends Signal[Nothing]
  case object RcvTimeout extends System
  final case class Terminated(ref: ActorRef) extends System

  sealed trait NonRcv extends System
  case object PostStop extends NonRcv
  final case class PostRestart(reason: Throwable) extends NonRcv


  implicit class Ops[A](val self: Signal[A]) extends AnyVal {
    def map[B](f: A => B): Signal[B] = self match {
      case Msg(cmd, sender) => Signal.Msg(f(cmd), sender)
      case signal: System   => signal
    }
  }
}