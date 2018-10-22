package com.evolutiongaming.safeakka.persistence

import akka.persistence.{SnapshotMetadata, SnapshotSelectionCriteria}


sealed trait PersistenceSignal[+A] {
  def map[B](ab: A => B): PersistenceSignal[B]
}

object PersistenceSignal {

  final case class Cmd[+A](cmd: A) extends PersistenceSignal[A] {
    def map[B](ab: A => B): Cmd[B] = copy(cmd = ab(cmd))
  }

  sealed trait System extends PersistenceSignal[Nothing] { self =>
    final def map[B](ab: Nothing => B): System = self
  }


  sealed trait SnapshotResponse extends System

  final case class SaveSnapshotSuccess(metadata: SnapshotMetadata) extends SnapshotResponse
  final case class SaveSnapshotFailure(metadata: SnapshotMetadata, cause: Throwable) extends SnapshotResponse


  final case class DeleteSnapshotSuccess(metadata: SnapshotMetadata) extends SnapshotResponse
  final case class DeleteSnapshotFailure(metadata: SnapshotMetadata, cause: Throwable) extends SnapshotResponse


  final case class DeleteSnapshotsSuccess(criteria: SnapshotSelectionCriteria) extends SnapshotResponse
  final case class DeleteSnapshotsFailure(criteria: SnapshotSelectionCriteria, cause: Throwable) extends SnapshotResponse


  sealed trait EventsResponse extends System
  final case class DeleteEventsSuccess(toSeqNr: SeqNr) extends EventsResponse
  final case class DeleteEventsFailure(toSeqNr: SeqNr, cause: Throwable) extends EventsResponse
}