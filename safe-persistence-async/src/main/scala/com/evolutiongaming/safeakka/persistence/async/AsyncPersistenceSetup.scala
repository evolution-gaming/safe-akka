package com.evolutiongaming.safeakka.persistence.async

import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.ActorLog
import com.evolutiongaming.safeakka.persistence._

trait AsyncPersistenceSetup[S, SS, C, E] {

  type Recovering = async.Recovering[SS, C, E]

  type AsyncSignalHandler = async.AsyncSignalHandler[SS, C, E]

  def persistenceId: String

  def log: ActorLog

  /**
    * called when recovering with events started, optional snapshot passed as argument
    */
  def onRecoveryStarted(
    offer: Option[SnapshotOffer[S]],
    journaller: Journaller,
    snapshotter: Snapshotter[S],
  ): Recovering

  /**
    * called when actor stopped during reading snapshot
    */
  def onStopped(seqNr: SeqNr): Unit
}

trait Recovering[S, C, E] {

  type AsyncSignalHandler = async.AsyncSignalHandler[S, C, E]

  /**
    * Initial state
    */
  def state: S

  /**
    * called to apply event to the state
    */
  def eventHandler(state: S, event: E, seqNr: SeqNr): S

  /**
    * called when recovering completed
    *
    * @return [[AsyncSignalHandler]]
    */
  def onCompleted(state: S, seqNr: SeqNr): AsyncSignalHandler

  def onStopped(state: S, seqNr: SeqNr): Unit
}

/**
  * This gives ability to process actor signals asynchronously and preserve order
  * First we use current state and signal to create async handler which is basically a Future[(S -> S)]
  * Then, when future completed, our handler (S -> S) will be applied and resulting state will be used for further signal handling
  */
trait AsyncSignalHandler[S, C, E] {

  /**
    * @param state - current state, however it might be stale at the moment of processing async handler
    * @return async handler for give signal, which will be applied against current state
    */
  def handler(state: S, seqNr: SeqNr, signal: PersistenceSignal[C]): AsyncHandler[S, E]

  /**
    * @param signals - enqueued signals left unprocessed upon actor shutdown
    */
  def onDropped(signals: Nel[PersistenceSignal[C]]): Unit
}
