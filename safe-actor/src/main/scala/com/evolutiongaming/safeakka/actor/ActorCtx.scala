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
  def watchWith(ref: ActorRef, msg: Any): ActorRef

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
    val context = actorContext
  }


  private[safeakka] trait ActorContextProxy extends ActorCtx {
    def context: ActorContext

    implicit def system = context.system
    implicit def dispatcher = context.dispatcher

    def self: ActorRef = context.self
    def setReceiveTimeout(timeout: Duration) = context.setReceiveTimeout(timeout)
    def children = context.children
    def child(name: String) = context.child(name)
    def watch(ref: ActorRef) = context.watchWith(ref, Signal.Terminated(ref))
    def watchWith(ref: ActorRef, msg: Any) = context.watchWith(ref, msg)
    def unwatch(ref: ActorRef) = context.unwatch(ref)
    def parent = context.parent
    def refFactory = context
  }
}