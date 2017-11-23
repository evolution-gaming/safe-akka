package com.evolutiongaming.safeakka.actor

import akka.actor.{Actor, ActorRef}

/**
  * Provides ability to have `tell` Function with proper toString & equals methods
  */
trait Tell[-T] extends (T => Unit)

object Tell {

  def empty[T]: Tell[T] = Empty

  def apply[T](ref: ActorRef): Tell[T] = Impl(ref)

  case class Impl[-T](ref: ActorRef) extends Tell[T] {
    def apply(msg: T): Unit = ref.tell(msg, Actor.noSender)
    override def toString() = s"Tell($ref)"
  }


  private object Empty extends Tell[Any] {
    def apply(msg: Any): Unit = {}
  }
}