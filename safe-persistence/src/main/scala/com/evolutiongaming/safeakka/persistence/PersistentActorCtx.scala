package com.evolutiongaming.safeakka.persistence

import akka.actor.{ActorContext, ActorRef}
import akka.persistence.PersistentActor
import com.evolutiongaming.safeakka.actor.ActorCtx

import scala.concurrent.duration.Duration

trait PersistentActorCtx[S] extends ActorCtx {
  def snapshotter: Snapshotter[S]
  def eventsourced: Eventsourced
}

object PersistentActorCtx {

  def apply[S](actor: PersistentActor): PersistentActorCtx[S] = {
    PersistentActorCtx(actor.context, Eventsourced(actor), Snapshotter(actor))
  }

  def apply[S](context: ActorContext, eventsourced: Eventsourced, snapshotter: Snapshotter[S]): PersistentActorCtx[S] = {

    val c = context
    val e = eventsourced
    val s = snapshotter

    new PersistentActorCtx[S] with ActorCtx.ActorContextProxy {
      def context = c
      def snapshotter = s
      def eventsourced = e
    }
  }


  def apply[S](ctx: ActorCtx, eventsourced: Eventsourced, snapshotter: Snapshotter[S]): PersistentActorCtx[S] = {
    
    val e = eventsourced
    val s = snapshotter

    new PersistentActorCtx[S] {
      def snapshotter = s
      def eventsourced = e
      def self = ctx.self
      def setReceiveTimeout(timeout: Duration) = ctx.setReceiveTimeout(timeout)
      def children = ctx.children
      def child(name: String) = ctx.child(name)
      implicit def dispatcher = ctx.dispatcher
      implicit def system = ctx.system
      def watch(ref: ActorRef) = ctx.watch(ref)
      def watchWith[A](ref: ActorRef, msg: A) = ctx.watchWith(ref, msg)
      def unwatch(ref: ActorRef) = ctx.unwatch(ref)
      def parent = ctx.parent
      def refFactory = ctx.refFactory
    }
  }
}