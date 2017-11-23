package akka.persistence

import com.evolutiongaming.safeakka.persistence.PersistenceSignal

object SnapshotResponse {

  def unapply(x: Any): Option[PersistenceSignal.SnapshotResponse] = PartialFunction.condOpt(x) {
    case x: SaveSnapshotSuccess    => PersistenceSignal.SaveSnapshotSuccess(x.metadata)
    case x: SaveSnapshotFailure    => PersistenceSignal.SaveSnapshotFailure(x.metadata, x.cause)
    case x: DeleteSnapshotSuccess  => PersistenceSignal.DeleteSnapshotSuccess(x.metadata)
    case x: DeleteSnapshotFailure  => PersistenceSignal.DeleteSnapshotFailure(x.metadata, x.cause)
    case x: DeleteSnapshotsSuccess => PersistenceSignal.DeleteSnapshotsSuccess(x.criteria)
    case x: DeleteSnapshotsFailure => PersistenceSignal.DeleteSnapshotsFailure(x.criteria, x.cause)
  }
}
