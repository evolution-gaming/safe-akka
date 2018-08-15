package com.evolutiongaming.safeakka.persistence

import akka.persistence.{SnapshotMetadata, SnapshotSelectionCriteria}


sealed trait PersistenceSignal[+C]

object PersistenceSignal {

  final case class Cmd[+C](cmd: C) extends PersistenceSignal[C]

  sealed trait System extends PersistenceSignal[Nothing]


  sealed trait SnapshotResponse extends System

  final case class SaveSnapshotFailure(metadata: SnapshotMetadata, cause: Throwable) extends SnapshotResponse
  final case class SaveSnapshotSuccess(metadata: SnapshotMetadata) extends SnapshotResponse


  final case class DeleteSnapshotSuccess(metadata: SnapshotMetadata) extends SnapshotResponse
  final case class DeleteSnapshotFailure(metadata: SnapshotMetadata, cause: Throwable) extends SnapshotResponse


  final case class DeleteSnapshotsSuccess(criteria: SnapshotSelectionCriteria) extends SnapshotResponse
  final case class DeleteSnapshotsFailure(criteria: SnapshotSelectionCriteria, cause: Throwable) extends SnapshotResponse


  sealed trait EventsResponse extends System
  final case class DeleteEventsSuccess(toSeqNr: SeqNr) extends EventsResponse
  final case class DeleteEventsFailure(toSeqNr: SeqNr, cause: Throwable) extends EventsResponse


  implicit class Ops[A](val self: PersistenceSignal[A]) extends AnyVal {
    def map[B](f: A => B): PersistenceSignal[B] = self match {
      case Cmd(cmd)       => Cmd(f(cmd))
      case signal: System => signal
    }
  }
}