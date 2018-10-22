package com.evolutiongaming.safeakka.persistence

import akka.actor.ActorRef
import com.evolutiongaming.safeakka.actor.{ActorLog, Signal}
import org.scalatest.{FunSuite, Matchers}

class PersistenceSetupSpec extends FunSuite with Matchers {

  val setup = new PersistenceSetup[Unit, Unit, String, String] {
    def persistenceId = "persistenceId"
    def log = ActorLog.empty
    def onRecoveryStarted(offer: Option[SnapshotOffer[Unit]]) = new Recovering {
      def state = {}
      def eventHandler(state: Unit, offer: WithNr[String]) = {}
      def onCompleted(state: WithNr[Unit]) = {
        def behavior(): PersistentBehavior[String, String] = PersistentBehavior[String, String] {
          case Signal.Msg(WithNr(PersistenceSignal.Cmd(cmd), _), _) => PersistentBehavior.persist(Record(cmd)) { _ => behavior() }
          case _                                                    => behavior()
        }

        behavior()
      }
      def onStopped(state: WithNr[Unit]) = {}
    }
    def onStopped(seqNr: SeqNr) = {}
  }

  test("map") {
    val setup2 = setup.map[Int, Int](_.toString, _.toInt, _.toString)

    val persist = setup2
      .onRecoveryStarted(None)
      .onCompleted(WithNr((), 0l))
      .asInstanceOf[PersistentBehavior.Rcv[Int, Int]]
      .onSignal(Signal.Msg(WithNr(PersistenceSignal.Cmd(0), 0l), ActorRef.noSender))
      .asInstanceOf[PersistentBehavior.Persist[Int, Int]]

    persist.events.map(_.event) shouldEqual List(0)
  }

  test("mapC") {
    val setup2 = setup.mapC[Int](_.toString)

    val persist = setup2
      .onRecoveryStarted(None)
      .onCompleted(WithNr((), 0l))
      .asInstanceOf[PersistentBehavior.Rcv[Int, String]]
      .onSignal(Signal.Msg(WithNr(PersistenceSignal.Cmd(0), 0l), ActorRef.noSender))
      .asInstanceOf[PersistentBehavior.Persist[Int, String]]

    persist.events.map(_.event) shouldEqual List("0")
  }

  test("mapE") {
    val setup2 = setup.mapE[Int](_.toInt, _.toString)

    val persist = setup2
      .onRecoveryStarted(None)
      .onCompleted(WithNr((), 0l))
      .asInstanceOf[PersistentBehavior.Rcv[String, Int]]
      .onSignal(Signal.Msg(WithNr(PersistenceSignal.Cmd("0"), 0l), ActorRef.noSender))
      .asInstanceOf[PersistentBehavior.Persist[String, Int]]

    persist.events.map(_.event) shouldEqual List(0)
  }
}
