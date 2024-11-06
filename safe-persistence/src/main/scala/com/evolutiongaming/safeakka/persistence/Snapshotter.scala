package com.evolutiongaming.safeakka.persistence

import akka.actor.ActorRef
import akka.persistence.*

import scala.annotation.nowarn

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

    @deprecated("use another `save` with seqNr as argument", "2.2.1")
    override def save(snapshot: B): Unit = self.save(f(snapshot))

    override def save(seqNr: SeqNr, snapshot: B): Unit = self.save(seqNr, f(snapshot))

    override def delete(seqNr: SeqNr): Unit = self.delete(seqNr)

    override def delete(criteria: SnapshotSelectionCriteria): Unit = self.delete(criteria)
  }
}

object Snapshotter {

  def empty[S]: Snapshotter[S] = new Snapshotter[S] {

    @deprecated("use another `save` with seqNr as argument", "2.2.1")
    override def save(snapshot: S): Unit = {}

    override def save(seqNr: SeqNr, snapshot: S): Unit = {}

    override def delete(sequenceNr: SeqNr): Unit = {}

    override def delete(criteria: SnapshotSelectionCriteria): Unit = {}
  }


  def apply[S](snapshotter: akka.persistence.Snapshotter): Snapshotter[S] = {
    SnapshotterFromPersistenceSnapshotter(snapshotter)
  }


  def apply[S](ref: ActorRef): Snapshotter[S] = new Snapshotter[S] {

    @deprecated("use another `save` with seqNr as argument", "2.2.1")
    override def save(snapshot: S): Unit = ref.tell(snapshot, ActorRef.noSender)

    override def save(seqNr: SeqNr, snapshot: S): Unit = save(snapshot): @nowarn("cat=deprecation")

    override def delete(seqNr: SeqNr): Unit = {}

    override def delete(criteria: SnapshotSelectionCriteria): Unit = {}
  }
}