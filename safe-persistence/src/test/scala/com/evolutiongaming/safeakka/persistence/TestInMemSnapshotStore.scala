package com.evolutiongaming.safeakka.persistence

import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.TreeMap
import scala.concurrent.Future

/**
 * Simple in-mem snapshot store for tests.
 *
 * An in-house snapshot store is made since Akka Persistence provides only the journal plugin for in-mem
 * and there doesn't seem to be any supported shared libraries for this with both Scala 3 and Akka 2.6 support.
 */
class TestInMemSnapshotStore extends SnapshotStore {

  import TestInMemSnapshotStore.*

  private val persistenceIdToSnapshots: TrieMap[String, Snapshots] = TrieMap.empty

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    val result = persistenceIdToSnapshots.get(persistenceId).flatMap(_.findLast(criteria))
    Future.successful(result)
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    val snapshots = persistenceIdToSnapshots.getOrElse(metadata.persistenceId, Snapshots.Empty)
    val newSnapshots = snapshots.append(SelectedSnapshot(metadata, snapshot))
    persistenceIdToSnapshots.put(metadata.persistenceId, newSnapshots)
    Future.unit
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    persistenceIdToSnapshots.updateWith(metadata.persistenceId)(_.map(_.deleteOne(metadata.sequenceNr)))
    Future.unit
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    persistenceIdToSnapshots.updateWith(persistenceId)(_.map(_.deleteMany(criteria)))
    Future.unit
  }
}

object TestInMemSnapshotStore {

  private final class Snapshots private(
    // using reverse order by seqNr in TreeMap so we can use iterators on values and nextOption to find the latest
    private val map: TreeMap[Long, SelectedSnapshot] =
      new TreeMap[Long, SelectedSnapshot]()(implicitly[Ordering[Long]].reverse)
  ) {
    def append(s: SelectedSnapshot): Snapshots = {
      new Snapshots(map = map.updated(s.metadata.sequenceNr, s))
    }

    def findLast(criteria: SnapshotSelectionCriteria): Option[SelectedSnapshot] = {
      criteriaIt(criteria)
        .nextOption()
    }

    def deleteOne(seqNr: Long): Snapshots = {
      new Snapshots(map = map.removed(seqNr))
    }

    def deleteMany(criteria: SnapshotSelectionCriteria): Snapshots = {
      val keysToDelete = criteriaIt(criteria).map(_.metadata.sequenceNr).toArray
      new Snapshots(map = map.removedAll(keysToDelete))
    }

    private def criteriaIt(criteria: SnapshotSelectionCriteria): Iterator[SelectedSnapshot] = {
      map.rangeFrom(criteria.maxSequenceNr)
        .rangeTo(criteria.minSequenceNr)
        .iterator
        .map(_._2)
        .filter(fitsTsCriteria(criteria))
    }
  }

  private object Snapshots {
    val Empty: Snapshots = new Snapshots()
  }

  private def fitsTsCriteria(criteria: SnapshotSelectionCriteria)(s: SelectedSnapshot): Boolean = {
    val ts = s.metadata.timestamp
    criteria.minTimestamp <= ts && ts <= criteria.maxTimestamp
  }
}
