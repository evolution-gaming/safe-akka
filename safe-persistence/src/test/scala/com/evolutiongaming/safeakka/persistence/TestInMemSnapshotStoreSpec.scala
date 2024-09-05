package com.evolutiongaming.safeakka.persistence

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

/**
 * Akka Persistence snapshot store TCK for [[TestInMemSnapshotStore]]
 */
class TestInMemSnapshotStoreSpec extends SnapshotStoreSpec(
  ConfigFactory.parseString(
    """
      |akka.persistence.snapshot-store.plugin = "com.evolution.safeakka.persistence.test-inmem-snapshot-store"
      |""".stripMargin)
)
