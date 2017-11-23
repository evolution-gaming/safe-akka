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
  def onRecoveryStarted(offer: Option[SnapshotOffer[S]]): Recovering[SS, E]

  /**
    * called when recovering completed, returns [[PersistentBehavior]]
    */
  def onRecoveryCompleted(state: WithNr[SS]): PersistentBehavior[C, E]

  /**
    * called when actor stopped during reading snapshot
    */
  def onRecoveryStopped(seqNr: SeqNr): Unit
}


/**
  * @param state        State to start recovering with
  * @param eventHandler called to apply event to the state
  * @param onStop       called when actor stopped during recovery
  */
case class Recovering[S, E](
  state: S,
  eventHandler: EventHandler[S, E],
  onStop: Callback[WithNr[S]] = Callback.empty[WithNr[S]])


/**
  * Typesafe clone of [[akka.persistence.SnapshotOffer]]
  */
case class SnapshotOffer[+T](metadata: SnapshotMetadata, snapshot: T)