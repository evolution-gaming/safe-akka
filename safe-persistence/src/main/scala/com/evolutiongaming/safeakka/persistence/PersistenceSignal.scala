package com.evolutiongaming.safeakka.persistence

import akka.persistence.{SnapshotMetadata, SnapshotSelectionCriteria}


sealed trait PersistenceSignal[+C]

object PersistenceSignal {

  case class Cmd[+C](cmd: C) extends PersistenceSignal[C]

  sealed trait System extends PersistenceSignal[Nothing]

  sealed trait SnapshotResponse extends System

  case class SaveSnapshotFailure(metadata: SnapshotMetadata, cause: Throwable) extends SnapshotResponse
  case class SaveSnapshotSuccess(metadata: SnapshotMetadata) extends SnapshotResponse

  case class DeleteSnapshotSuccess(metadata: SnapshotMetadata) extends SnapshotResponse
  case class DeleteSnapshotsSuccess(criteria: SnapshotSelectionCriteria) extends SnapshotResponse

  case class DeleteSnapshotFailure(metadata: SnapshotMetadata, cause: Throwable) extends SnapshotResponse
  case class DeleteSnapshotsFailure(criteria: SnapshotSelectionCriteria, cause: Throwable) extends SnapshotResponse


  implicit class Ops[A](val self: PersistenceSignal[A]) extends AnyVal {
    def map[B](f: A => B): PersistenceSignal[B] = self match {
      case Cmd(cmd)       => Cmd(f(cmd))
      case signal: System => signal
    }
  }
}