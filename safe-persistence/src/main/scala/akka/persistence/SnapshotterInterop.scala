package akka.persistence

import akka.persistence.SnapshotProtocol.SaveSnapshot
import com.evolutiongaming.safeakka
import com.evolutiongaming.safeakka.persistence.SeqNr

object SnapshotterInterop {

  def apply[A](snapshotter: Snapshotter): safeakka.persistence.Snapshotter[A] = {

    new safeakka.persistence.Snapshotter[A] {

      def save(snapshot: A) = snapshotter.saveSnapshot(snapshot)

      def save(seqNr: SeqNr, snapshot: A) = {
        val saveSnapshot = SaveSnapshot(SnapshotMetadata(snapshotter.snapshotterId, seqNr), snapshot)
        snapshotter.snapshotStore.tell(saveSnapshot, snapshotter.self)
      }

      def delete(seqNr: SeqNr) = snapshotter.deleteSnapshot(seqNr)

      def delete(criteria: SnapshotSelectionCriteria) = snapshotter.deleteSnapshots(criteria)
    }
  }
}
