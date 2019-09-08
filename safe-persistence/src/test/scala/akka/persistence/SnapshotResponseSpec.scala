package akka.persistence

import com.evolutiongaming.safeakka.persistence.{PersistenceSignal => Signal}
import org.scalatest.{FunSuite, Matchers}

import scala.util.control.NoStackTrace

class SnapshotResponseSpec extends FunSuite with Matchers {

  val metadata = SnapshotMetadata("persistenceId", 0L, 0L)
  val criteria = SnapshotSelectionCriteria()

  test("unapply SaveSnapshotSuccess") {
    SnapshotResponse.unapply(SaveSnapshotSuccess(metadata)) shouldEqual Some(Signal.SaveSnapshotSuccess(metadata))
  }

  test("unapply SaveSnapshotFailure") {
    SnapshotResponse.unapply(SaveSnapshotFailure(metadata, Failure)) shouldEqual Some(Signal.SaveSnapshotFailure(metadata, Failure))
  }

  test("unapply DeleteSnapshotSuccess") {
    SnapshotResponse.unapply(DeleteSnapshotSuccess(metadata)) shouldEqual Some(Signal.DeleteSnapshotSuccess(metadata))
  }

  test("unapply DeleteSnapshotFailure") {
    SnapshotResponse.unapply(DeleteSnapshotFailure(metadata, Failure)) shouldEqual Some(Signal.DeleteSnapshotFailure(metadata, Failure))
  }

  test("unapply DeleteSnapshotsSuccess") {
    SnapshotResponse.unapply(DeleteSnapshotsSuccess(criteria)) shouldEqual Some(Signal.DeleteSnapshotsSuccess(criteria))
  }

  test("unapply DeleteSnapshotsFailure") {
    SnapshotResponse.unapply(DeleteSnapshotsFailure(criteria, Failure)) shouldEqual Some(Signal.DeleteSnapshotsFailure(criteria, Failure))
  }

  object Failure extends RuntimeException with NoStackTrace
}
