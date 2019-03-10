package com.evolutiongaming.safeakka.persistence

import akka.actor.ActorRef
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.ActorLog
import org.scalatest.{FunSuite, Matchers}

class PersistenceSetupSpec extends FunSuite with Matchers {

  val actorLog = ActorLog.empty.prefixed("PersistenceSetupSpec")

  val setup = new PersistenceSetup[Unit, Unit, String, String] {

    def persistenceId = "persistenceId"

    def log = actorLog

    def journalId = Some("journalId")

    def snapshotId = Some("journalId")

    def onRecoveryStarted(
      offer: Option[SnapshotOffer[Unit]],
      journaller: Journaller,
      snapshotter: Snapshotter[Unit]) = new Recovering {

      def state = {}

      def eventHandler(state: Unit, event: String, seqNr: SeqNr) = {}

      def onCompleted(state: Unit, seqNr: SeqNr) = {
        def behavior(): PersistentBehavior[String, String] = PersistentBehavior[String, String] { (signal, _) =>
          signal match {
            case PersistenceSignal.Cmd(cmd, _) => PersistentBehavior.persist(Nel(Record(cmd)), _ => behavior(), _ => ())
            case _                             => behavior()
          }
        }

        behavior()
      }
      def onStopped(state: Unit, seqNr: SeqNr) = {}
    }
    def onStopped(seqNr: SeqNr) = {}
  }

  test("map") {
    val setup2 = setup.mapRecovering(_.map[Int, Int](_.toString, _.toInt, _.toString))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0l)
      .asInstanceOf[PersistentBehavior.Rcv[Int, Int]]
      .onSignal(PersistenceSignal.Cmd(0, ActorRef.noSender), 0l)
      .asInstanceOf[PersistentBehavior.Persist[Int, Int]]

    persist.records.map(_.event) shouldEqual Nel(0)
  }

  test("mapCommand") {
    val setup2 = setup.mapRecovering(_.mapBehavior(_.mapCommand[Int](_.toString)))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0l)
      .asInstanceOf[PersistentBehavior.Rcv[Int, String]]
      .onSignal(PersistenceSignal.Cmd(0, ActorRef.noSender), 0l)
      .asInstanceOf[PersistentBehavior.Persist[Int, String]]

    persist.records.map(_.event) shouldEqual Nel("0")
  }

  test("mapEvent") {
    val setup2 = setup.mapRecovering(_.mapEvent[Int](_.toInt, _.toString))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0l)
      .asInstanceOf[PersistentBehavior.Rcv[String, Int]]
      .onSignal(PersistenceSignal.Cmd("0", ActorRef.noSender), 0l)
      .asInstanceOf[PersistentBehavior.Persist[String, Int]]

    persist.records.map(_.event) shouldEqual Nel(0)
  }

  private def compare(a: PersistenceSetup[_, _, _, _], b: PersistenceSetup[_, _, _, _]) = {
    a.persistenceId shouldEqual b.persistenceId
    a.journalId shouldEqual b.journalId
    a.snapshotId shouldEqual b.snapshotId
    a.log shouldEqual b.log
  }
}
