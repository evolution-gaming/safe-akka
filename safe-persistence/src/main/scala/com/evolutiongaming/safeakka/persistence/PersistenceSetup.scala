package com.evolutiongaming.safeakka.persistence

import akka.persistence.SnapshotMetadata
import com.evolutiongaming.safeakka.actor.ActorLog


/**
  * Implement to start [[SafePersistentActor]]
  */
trait PersistenceSetup[S, SS, C, E] {

  def persistenceId: String

  def log: ActorLog

  /**
    * called when recovering with events started, optional snapshot passed as argument
    */
  def onRecoveryStarted(offer: Option[SnapshotOffer[S]]): Recovering[SS, C, E]

  /**
    * called when actor stopped during reading snapshot
    */
  def onStopped(seqNr: SeqNr): Unit
}


trait Recovering[S, C, E] {

  def state: S

  /**
    * called to apply event to the state
    */
  def eventHandler(state: S, offer: WithNr[E]): S

  /**
    * called when recovering completed
    *
    * @return [[PersistentBehavior]]
    */
  def onCompleted(state: WithNr[S]): PersistentBehavior[C, E]

  /**
    * called when actor stopped during recovery
    */
  def onStopped(state: WithNr[S]): Unit
}

object Recovering {

  def apply[S, C, E](
    state: S,
    eventHandler: EventHandler[S, E],
    onCompleted: OnRecoveryCompleted[S, C, E],
    onStopped: Callback[WithNr[S]] = Callback.empty): Recovering[S, C, E] = {

    val stateArg = state
    val eventHandlerArg = eventHandler
    val onCompletedArg = onCompleted
    val onStoppedArg = onStopped

    new Recovering[S, C, E] {
      def state: S = stateArg
      def eventHandler(state: S, offer: WithNr[E]): S = eventHandlerArg(state, offer)
      def onCompleted(state: WithNr[S]): PersistentBehavior[C, E] = onCompletedArg(state)
      def onStopped(state: WithNr[S]): Unit = onStoppedArg(state)
    }
  }
}


/**
  * Typesafe clone of [[akka.persistence.SnapshotOffer]]
  */
case class SnapshotOffer[+T](metadata: SnapshotMetadata, snapshot: T)