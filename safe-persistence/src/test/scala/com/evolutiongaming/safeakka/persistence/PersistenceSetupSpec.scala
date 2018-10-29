package com.evolutiongaming.safeakka.persistence

import akka.actor.ActorRef
import com.evolutiongaming.safeakka.actor.ActorLog
import org.scalatest.{FunSuite, Matchers}

class PersistenceSetupSpec extends FunSuite with Matchers {

  val setup = new PersistenceSetup[Unit, Unit, String, String] {

    def persistenceId = "persistenceId"

    def log = ActorLog.empty

    def journalId = Some("journalId")

    def snapshotId = Some("journalId")

    def onRecoveryStarted(
      offer: Option[SnapshotOffer[Unit]],
      journal: Journaller,
      snapshotter: Snapshotter[Unit]) = new Recovering {

      def state = {}

      def eventHandler(state: Unit, offer: String, seqNr: SeqNr) = {}

      def onCompleted(state: Unit, seqNr: SeqNr) = {
        def behavior(): PersistentBehavior[String, String] = PersistentBehavior[String, String] { (signal, _) =>
          signal match {
            case PersistenceSignal.Cmd(cmd, _) => PersistentBehavior.persist(Record(cmd)) { _ => behavior() }
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
    val setup2 = setup.mapRecovery(_.map[Int, Int](_.toString, _.toInt, _.toString))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0l)
      .asInstanceOf[PersistentBehavior.Rcv[Int, Int]]
      .onSignal(PersistenceSignal.Cmd(0, ActorRef.noSender), 0l)
      .asInstanceOf[PersistentBehavior.Persist[Int, Int]]

    persist.events.map(_.event) shouldEqual List(0)
  }

  test("mapCommand") {
    val setup2 = setup.mapRecovery(_.mapBehavior(_.mapCommand[Int](_.toString)))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0l)
      .asInstanceOf[PersistentBehavior.Rcv[Int, String]]
      .onSignal(PersistenceSignal.Cmd(0, ActorRef.noSender), 0l)
      .asInstanceOf[PersistentBehavior.Persist[Int, String]]

    persist.events.map(_.event) shouldEqual List("0")
  }

  test("mapEvent") {
    val setup2 = setup.mapRecovery(_.mapEvent[Int](_.toInt, _.toString))
    compare(setup2, setup)

    val persist = setup2
      .onRecoveryStarted(None, Journaller.empty, Snapshotter.empty)
      .onCompleted((), 0l)
      .asInstanceOf[PersistentBehavior.Rcv[String, Int]]
      .onSignal(PersistenceSignal.Cmd("0", ActorRef.noSender), 0l)
      .asInstanceOf[PersistentBehavior.Persist[String, Int]]

    persist.events.map(_.event) shouldEqual List(0)
  }

  private def compare(a: PersistenceSetup[_, _, _, _], b: PersistenceSetup[_, _, _, _]) = {
    a.persistenceId shouldEqual b.persistenceId
    a.journalId shouldEqual b.journalId
    a.snapshotId shouldEqual b.snapshotId
    a.log shouldEqual b.log
  }
}
