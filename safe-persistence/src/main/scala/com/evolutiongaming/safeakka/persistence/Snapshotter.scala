package com.evolutiongaming.safeakka.persistence

import akka.actor.ActorRef
import akka.persistence._

trait Snapshotter[-S] {
  /**
    * @see [[akka.persistence.Snapshotter.loadSnapshot]]
    */
  def load(persistenceId: String, criteria: SnapshotSelectionCriteria, toSeqNr: SeqNr): Unit

  /**
    * @see [[akka.persistence.Snapshotter.saveSnapshot]]
    */
  def save(snapshot: S): Unit

  /**
    * @see [[akka.persistence.Snapshotter.deleteSnapshot]]
    */
  def delete(seqNr: SeqNr): Unit

  /**
    * @see [[akka.persistence.Snapshotter.deleteSnapshots]]
    */
  def delete(criteria: SnapshotSelectionCriteria): Unit
}

object Snapshotter {

  def empty[S]: Snapshotter[S] = Empty

  def apply[S](snapshotter: akka.persistence.Snapshotter): Snapshotter[S] = new Snapshotter[S] {

    def save(snapshot: S): Unit = snapshotter.saveSnapshot(snapshot)

    def delete(seqNr: SeqNr): Unit = snapshotter.deleteSnapshot(seqNr)

    def load(persistenceId: String, criteria: SnapshotSelectionCriteria, toSeqNr: SeqNr): Unit =
      snapshotter.loadSnapshot(persistenceId, criteria, toSeqNr)

    def delete(criteria: SnapshotSelectionCriteria): Unit = snapshotter.deleteSnapshots(criteria)
  }

  def apply[S](ref: ActorRef): Snapshotter[S] = new Snapshotter[S] {
    def save(snapshot: S): Unit = ref.tell(snapshot, ActorRef.noSender)
    def delete(seqNr: SeqNr): Unit = {}
    def delete(criteria: SnapshotSelectionCriteria): Unit = {}
    def load(persistenceId: String, criteria: SnapshotSelectionCriteria, toSeqNr: SeqNr): Unit = {}
  }


  private object Empty extends Snapshotter[Any] {
    def load(persistenceId: String, criteria: SnapshotSelectionCriteria, toSeqNr: SeqNr): Unit = {}
    def save(snapshot: Any): Unit = {}
    def delete(sequenceNr: SeqNr): Unit = {}
    def delete(criteria: SnapshotSelectionCriteria): Unit = {}
  }
}