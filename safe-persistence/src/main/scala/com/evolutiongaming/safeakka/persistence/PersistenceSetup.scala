package com.evolutiongaming.safeakka.persistence

import akka.persistence.SnapshotMetadata
import com.evolutiongaming.safeakka.actor.ActorLog
import com.evolutiongaming.safeakka.persistence


/**
  * Implement to start [[SafePersistentActor]]
  */
trait PersistenceSetup[S, SS, C, E] { self =>

  type Recovering = persistence.Recovering[SS, C, E]


  def persistenceId: String

  def log: ActorLog

  /**
    * called when recovering with events started, optional snapshot passed as argument
    */
  def onRecoveryStarted(offer: Option[SnapshotOffer[S]]): Recovering

  /**
    * called when actor stopped during reading snapshot
    */
  def onStopped(seqNr: SeqNr): Unit


  final def mapC[CC](f: CC => C): PersistenceSetup[S, SS, CC, E] = new PersistenceSetup[S, SS, CC, E] {

    def persistenceId = self.persistenceId

    def log = self.log

    def onRecoveryStarted(offer: Option[SnapshotOffer[S]]) = self.onRecoveryStarted(offer).mapC(f)

    def onStopped(seqNr: SeqNr) = self.onStopped(seqNr)
  }


  final def mapE[EE](fee: E => EE, fe: EE => E): PersistenceSetup[S, SS, C, EE] = new PersistenceSetup[S, SS, C, EE] {

    def persistenceId = self.persistenceId

    def log = self.log

    def onRecoveryStarted(offer: Option[SnapshotOffer[S]]) = {
      self.onRecoveryStarted(offer).mapE(fee, fe)
    }

    def onStopped(seqNr: SeqNr) = self.onStopped(seqNr)
  }


  final def map[CC, EE](fc: CC => C, fee: E => EE, fe: EE => E): PersistenceSetup[S, SS, CC, EE] = new PersistenceSetup[S, SS, CC, EE] {

    def persistenceId = self.persistenceId

    def log = self.log

    def onRecoveryStarted(offer: Option[SnapshotOffer[S]]) = {
      self.onRecoveryStarted(offer).map(fc, fee, fe)
    }

    def onStopped(seqNr: SeqNr) = self.onStopped(seqNr)
  }
}


trait Recovering[S, C, E] { self =>

  /**
    * Initial state
    */
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


  final def mapC[CC](f: CC => C): Recovering[S, CC, E] = new Recovering[S, CC, E] {

    def state = self.state

    def eventHandler(state: S, offer: WithNr[E]) = self.eventHandler(state, offer)

    def onCompleted(state: WithNr[S]) = self.onCompleted(state).mapC(f)

    def onStopped(state: WithNr[S]) = onStopped(state)
  }


  final def mapE[EE](fee: E => EE, fe: EE => E): Recovering[S, C, EE] = new Recovering[S, C, EE] {

    def state = self.state

    def eventHandler(state: S, offer: WithNr[EE]) = self.eventHandler(state, offer.map(fe))

    def onCompleted(state: WithNr[S]) = self.onCompleted(state).mapE(fee)

    def onStopped(state: WithNr[S]) = self.onStopped(state)
  }


  final def map[CC, EE](fc: CC => C, fee: E => EE, fe: EE => E): Recovering[S, CC, EE] = new Recovering[S, CC, EE] {

    def state = self.state

    def eventHandler(state: S, offer: WithNr[EE]) = self.eventHandler(state, offer.map(fe))

    def onCompleted(state: WithNr[S]) = self.onCompleted(state).map(fc, fee)

    def onStopped(state: WithNr[S]) = self.onStopped(state)
  }
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
final case class SnapshotOffer[+A](metadata: SnapshotMetadata, snapshot: A) {

  def map[B](ab: A => B): SnapshotOffer[B] = copy(snapshot = ab(snapshot))
}