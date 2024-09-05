package com.evolutiongaming.safeakka.persistence

import akka.actor.ActorRef
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.ActorLog
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PersistenceSetupSpec extends AnyFunSuite with Matchers {

  val actorLog: ActorLog = ActorLog.empty.prefixed("PersistenceSetupSpec")

  val setup: PersistenceSetup[Unit, Unit, String, String] = new PersistenceSetup[Unit, Unit, String, String] {

    override def persistenceId: String = "persistenceId"

    override def log: ActorLog = actorLog

    override def journalId: Option[String] = Some("journalId")

    override def snapshotId: Option[String] = Some("journalId")

    override def onRecoveryStarted(
      offer: Option[SnapshotOffer[Unit]],
      journaller: Journaller,
      snapshotter: Snapshotter[Unit]): Recovering = new Recovering {

      override def state: Unit = {}

      override def eventHandler(state: Unit, event: String, seqNr: SeqNr): Unit = {}

      override def onCompleted(state: Unit, seqNr: SeqNr): PersistentBehavior[String, String] = {
        def behavior(): PersistentBehavior[String, String] = PersistentBehavior[String, String] { (signal, _) =>
          signal match {
            case PersistenceSignal.Cmd(cmd, _) => PersistentBehavior.persist(Nel(Record(cmd)), _ => behavior(), _ => ())
            case _                             => behavior()
          }
        }

        behavior()
      }
      override def onStopped(state: Unit, seqNr: SeqNr): Unit = {}
    }
    override def onStopped(seqNr: SeqNr): Unit = {}
  }

  test("map") {
    val setup2 = setup.mapRecovering(_.map[Int, Int](_.toString, _.toInt, _.toString))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0L)
      .asInstanceOf[PersistentBehavior.Rcv[Int, Int]]
      .onSignal(PersistenceSignal.Cmd(0, ActorRef.noSender), 0L)
      .asInstanceOf[PersistentBehavior.Persist[Int, Int]]

    persist.records.map(_.event) shouldEqual Nel(0)
  }

  test("mapCommand") {
    val setup2 = setup.mapRecovering(_.mapBehavior(_.mapCommand[Int](_.toString)))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0L)
      .asInstanceOf[PersistentBehavior.Rcv[Int, String]]
      .onSignal(PersistenceSignal.Cmd(0, ActorRef.noSender), 0L)
      .asInstanceOf[PersistentBehavior.Persist[Int, String]]

    persist.records.map(_.event) shouldEqual Nel("0")
  }

  test("mapEvent") {
    val setup2 = setup.mapRecovering(_.mapEvent[Int](_.toInt, _.toString))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0L)
      .asInstanceOf[PersistentBehavior.Rcv[String, Int]]
      .onSignal(PersistenceSignal.Cmd("0", ActorRef.noSender), 0L)
      .asInstanceOf[PersistentBehavior.Persist[String, Int]]

    persist.records.map(_.event) shouldEqual Nel(0)
  }

  private def compare(a: PersistenceSetup[?, ?, ?, ?], b: PersistenceSetup[?, ?, ?, ?]) = {
    a.persistenceId shouldEqual b.persistenceId
    a.journalId shouldEqual b.journalId
    a.snapshotId shouldEqual b.snapshotId
    a.log shouldEqual b.log
  }
}
