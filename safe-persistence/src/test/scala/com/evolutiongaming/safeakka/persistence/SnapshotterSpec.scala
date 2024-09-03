package com.evolutiongaming.safeakka.persistence

import akka.persistence.SnapshotSelectionCriteria
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn

class SnapshotterSpec extends AnyWordSpec with ActorSpec with Matchers {

  "Snapshotter" should {

    "save" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.save(1L, ())
      expectMsg(())
    }

    "save no seqNr" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.save(()): @nowarn("cat=deprecation")
      expectMsg(())
    }

    "delete" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.delete(0L)
    }

    "delete by criteria" in new ActorScope {
      val snapshotter = Snapshotter[Unit](testActor)
      snapshotter.delete(SnapshotSelectionCriteria())
    }
  }
}
