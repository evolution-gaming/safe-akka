package akka.persistence

import com.evolutiongaming.safeakka

/*
The class has been renamed to SnapshotterInterop by Yaroslav Klymko after 3.0.0 release, but the changes never got
to release.
The class has been restored with deprecation to satisfy MiMa bincompat report.
 */

@deprecated(message = "use SnapshotterInterop", since = "3.1.0")
object SnapshotterFromPersistenceSnapshotter {
  def apply[A](snapshotter: Snapshotter): safeakka.persistence.Snapshotter[A] = SnapshotterInterop.apply(snapshotter)
}