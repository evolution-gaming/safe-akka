package com.evolutiongaming.safeakka.persistence

import akka.persistence.SnapshotSelectionCriteria
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import org.scalatest.{Matchers, WordSpec}

class SnapshotterSpec extends WordSpec with ActorSpec with Matchers {

  "Snapshotter" should {

    /*"load" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.load("persistenceId", SnapshotSelectionCriteria(), 0l)
    }*/

    "save" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.save(())
      expectMsg(())
    }

    "delete" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.delete(0l)
    }

    "delete by criteria" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.delete(SnapshotSelectionCriteria())
    }
  }
}
