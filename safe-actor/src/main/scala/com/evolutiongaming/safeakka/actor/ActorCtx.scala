package com.evolutiongaming.safeakka.actor

import akka.actor.{ActorContext, ActorRef, ActorRefFactory, ActorSystem}

import scala.collection.immutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration

trait ActorCtx {

  def self: ActorRef

  /**
    * @see [[akka.actor.ActorContext.setReceiveTimeout]]
    */
  def setReceiveTimeout(timeout: Duration): Unit

  /**
    * @see [[akka.actor.ActorContext.children]]
    */
  def children: immutable.Iterable[ActorRef]

  /**
    * @see [[akka.actor.ActorContext.child]]
    */
  def child(name: String): Option[ActorRef]

  /**
    * @see [[akka.actor.ActorContext.dispatcher]]
    */
  implicit def dispatcher: ExecutionContextExecutor

  /**
    * @see [[akka.actor.ActorContext.system]]
    */
  implicit def system: ActorSystem

  /**
    * @see [[akka.actor.ActorContext.watch]]
    */
  def watch(ref: ActorRef): ActorRef

  /**
    * @see [[akka.actor.ActorContext.watchWith]]
    */
  def watchWith[A](ref: ActorRef, msg: A): ActorRef

  /**
    * @see [[akka.actor.ActorContext.unwatch]]
    */
  def unwatch(ref: ActorRef): ActorRef

  /**
    * @see [[akka.actor.ActorContext.parent]]
    */
  def parent: ActorRef

  def refFactory: ActorRefFactory
}

object ActorCtx {
  def apply(actorContext: ActorContext): ActorCtx = new ActorContextProxy {
    override val context: ActorContext = actorContext
  }


  private[safeakka] trait ActorContextProxy extends ActorCtx {
    def context: ActorContext

    override implicit def system: ActorSystem = context.system
    override implicit def dispatcher: ExecutionContextExecutor = context.dispatcher

    override def self: ActorRef = context.self
    override def setReceiveTimeout(timeout: Duration): Unit = context.setReceiveTimeout(timeout)
    override def children: immutable.Iterable[Sender] = context.children
    override def child(name: String): Option[Sender] = context.child(name)
    override def watch(ref: ActorRef): ActorRef = context.watchWith(ref, Signal.Terminated(ref))
    override def watchWith[A](ref: ActorRef, msg: A): ActorRef = context.watchWith(ref, msg)
    override def unwatch(ref: ActorRef): ActorRef = context.unwatch(ref)
    override def parent: ActorRef = context.parent
    override def refFactory: ActorContext = context
  }
}