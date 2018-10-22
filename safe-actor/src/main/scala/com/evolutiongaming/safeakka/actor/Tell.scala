package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ActorRef}

/**
  * Provides ability to have `tell` Function with proper toString & equals methods
  */
trait Tell[-A] extends (A => Unit)

object Tell {

  def empty[A]: Tell[A] = Empty

  def apply[A](ref: ActorRef): Tell[A] = Impl(ref)

  final case class Impl[-A](ref: ActorRef) extends Tell[A] {
    def apply(msg: A): Unit = ref.tell(msg, Actor.noSender)
    override def toString() = s"Tell($ref)"
  }


  private object Empty extends Tell[Any] {
    def apply(msg: Any): Unit = {}
  }
}