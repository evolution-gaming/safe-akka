package com.evolutiongaming.safeakka.persistence

import akka.persistence.SnapshotMetadata
import com.evolutiongaming.safeakka.actor.ActorLog
import com.evolutiongaming.safeakka.persistence


/**
  * Implement to start [[SafePersistentActor]]
  */
trait PersistenceSetup[S, SS, C, E] { self =>

  type Recovering = persistence.Recovering[SS, C, E]

  /**
    * See [[akka.persistence.PersistenceIdentity.persistenceId]]
    */
  def persistenceId: String

  /**
    * See [[akka.persistence.PersistenceIdentity.journalPluginId]]
    */
  def journalId: Option[String]

  /**
    * See [[akka.persistence.PersistenceIdentity.snapshotPluginId]]
    */
  def snapshotId: Option[String]


  def log: ActorLog

  /**
    * called when recovering with events started, optional snapshot passed as argument
    */
  def onRecoveryStarted(
    offer: Option[SnapshotOffer[S]],
    journaller: Journaller,
    snapshotter: Snapshotter[S]): Recovering

  /**
    * called when actor stopped during reading snapshot
    */
  def onStopped(seqNr: SeqNr): Unit


  final def mapRecovering[SSS, CC, EE](f: Recovering => persistence.Recovering[SSS, CC, EE]): persistence.PersistenceSetup[S, SSS, CC, EE] = {

    new PersistenceSetup[S, SSS, CC, EE] {

      def persistenceId = self.persistenceId

      def log = self.log

      def journalId = self.journalId

      def snapshotId = self.snapshotId

      def onRecoveryStarted(
        offer: Option[SnapshotOffer[S]],
        journaller: Journaller,
        snapshotter: Snapshotter[S]) = {

        f(self.onRecoveryStarted(offer, journaller, snapshotter))
      }

      def onStopped(seqNr: SeqNr) = self.onStopped(seqNr)
    }
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
  def eventHandler(state: S, event: E, seqNr: SeqNr): S

  /**
    * called when recovering completed
    *
    * @return [[PersistentBehavior]]
    */
  def onCompleted(state: S, seqNr: SeqNr): PersistentBehavior[C, E]

  /**
    * called when actor stopped during recovery
    */
  def onStopped(state: S, seqNr: SeqNr): Unit


  final def mapEvent[EE](fee: E => EE, fe: EE => E): Recovering[S, C, EE] = new Recovering[S, C, EE] {

    def state = self.state

    def eventHandler(state: S, event: EE, seqNr: SeqNr) = self.eventHandler(state, fe(event), seqNr)

    def onCompleted(state: S, seqNr: SeqNr) = self.onCompleted(state, seqNr).mapEvent(fee)

    def onStopped(state: S, seqNr: SeqNr) = self.onStopped(state, seqNr)
  }


  final def map[CC, EE](fc: CC => C, fee: E => EE, fe: EE => E): Recovering[S, CC, EE] = new Recovering[S, CC, EE] {

    def state = self.state

    def eventHandler(state: S, event: EE, seqNr: SeqNr) = self.eventHandler(state, fe(event), seqNr)

    def onCompleted(state: S, seqNr: SeqNr) = self.onCompleted(state, seqNr).map(fc, fee)

    def onStopped(state: S, seqNr: SeqNr) = self.onStopped(state, seqNr)
  }

  final def mapBehavior[CC](f: PersistentBehavior[C, E] => PersistentBehavior[CC, E]): Recovering[S, CC, E] = new Recovering[S, CC, E] {

    def state = self.state

    def eventHandler(state: S, event: E, seqNr: SeqNr) = self.eventHandler(state, event, seqNr)

    def onCompleted(state: S, seqNr: SeqNr) = f(self.onCompleted(state, seqNr))

    def onStopped(state: S, seqNr: SeqNr) = self.onStopped(state, seqNr)
  }
}


/**
  * Typesafe clone of [[akka.persistence.SnapshotOffer]]
  */
final case class SnapshotOffer[+A](metadata: SnapshotMetadata, snapshot: A) {

  def map[B](ab: A => B): SnapshotOffer[B] = copy(snapshot = ab(snapshot))
}