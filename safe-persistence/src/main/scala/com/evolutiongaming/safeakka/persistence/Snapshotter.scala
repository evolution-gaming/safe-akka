package com.evolutiongaming.safeakka.persistence

import akka.actor.ActorRef
import akka.persistence._

trait Snapshotter[-A] { self =>

  /**
    * @see [[akka.persistence.Snapshotter.saveSnapshot]]
    */
  @deprecated("use another `save` with seqNr as argument", "2.2.1")
  def save(snapshot: A): Unit

  /**
    * @see [[akka.persistence.Snapshotter.saveSnapshot]]
    */
  def save(seqNr: SeqNr, snapshot: A): Unit

  /**
    * @see [[akka.persistence.Snapshotter.deleteSnapshot]]
    */
  def delete(seqNr: SeqNr): Unit

  /**
    * @see [[akka.persistence.Snapshotter.deleteSnapshots]]
    */
  def delete(criteria: SnapshotSelectionCriteria): Unit


  final def map[B](f: B => A): Snapshotter[B] = new Snapshotter[B] {

    def save(snapshot: B) = self.save(f(snapshot))

    def save(seqNr: SeqNr, snapshot: B) = self.save(seqNr, f(snapshot))

    def delete(seqNr: SeqNr) = self.delete(seqNr)

    def delete(criteria: SnapshotSelectionCriteria) = self.delete(criteria)
  }
}

object Snapshotter {

  def empty[S]: Snapshotter[S] = new Snapshotter[S] {

    def save(snapshot: S) = {}

    def save(seqNr: SeqNr, snapshot: S) = {}

    def delete(sequenceNr: SeqNr) = {}

    def delete(criteria: SnapshotSelectionCriteria) = {}
  }


  def apply[S](snapshotter: akka.persistence.Snapshotter): Snapshotter[S] = {
    SnapshotterInterop(snapshotter)
  }


  def apply[S](ref: ActorRef): Snapshotter[S] = new Snapshotter[S] {

    def save(snapshot: S) = ref.tell(snapshot, ActorRef.noSender)

    def save(seqNr: SeqNr, snapshot: S) = save(snapshot)

    def delete(seqNr: SeqNr) = {}

    def delete(criteria: SnapshotSelectionCriteria) = {}
  }
}