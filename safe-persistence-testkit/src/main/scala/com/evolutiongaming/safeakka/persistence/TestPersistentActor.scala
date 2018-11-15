package com.evolutiongaming.safeakka.persistence

import com.evolutiongaming.safeakka.actor.{Sender, Unapply, WithSender}

import scala.util.Success


class TestPersistentActor[S, SS, C, E](
  val setupPersistentActor: SetupPersistentActor[S, SS, C, E],
  val journaller: Journaller,
  val snapshotter: Snapshotter[S],
  val asS: Unapply[S],
  val asC: Unapply[C],
  val asE: Unapply[E]) extends SafePersistentActor.PersistentLike[S, SS, C, E] {

  private var seqNr: SeqNr = 0L
  private var stash: List[WithSender[Any]] = Nil

  def receive = rcvRecover orElse {
    case msg => stash = WithSender(msg, sender()) :: stash
  }

  override def onSnapshotOffer(snapshot: SnapshotOffer[S]): Unit = {
    seqNr = snapshot.metadata.sequenceNr
    super.onSnapshotOffer(snapshot)
  }

  override def onEvent(event: E, seqNr: SeqNr): Unit = {
    this.seqNr = seqNr + 1
    super.onEvent(event, this.seqNr)
  }

  override def onRecoveryCompleted(seqNr: SeqNr): Unit = {
    super.onRecoveryCompleted(seqNr)

    stash.foldRight(()) { (withSender, _) =>
      val seqNr = this.seqNr
      val sender = withSender.sender getOrElse Sender(context.system.deadLetters)
      withSender.msg match {
        case asC(cmd) => onCmd(PersistenceSignal.Cmd(cmd, sender), seqNr)
        case msg      => onAny(msg, seqNr, sender)
      }
    }
    stash = Nil

    context.become(rcvCommand)
  }

  def onPersist(behavior: PersistentBehavior.Persist[C, E], onStop: SeqNr => Unit) = {
    behavior.records.foreach { record =>
      seqNr += 1
      record.onPersisted(Success(seqNr))
    }
    nextPhase(behavior.onPersisted(seqNr), onStop)
  }

  def lastSeqNr() = seqNr

  override def toString = s"TestPersistentActor($persistenceId)"
}

object TestPersistentActor {

  def apply[S: Unapply, SS, C: Unapply, E: Unapply](
    setup: SetupPersistentActor[S, SS, C, E],
    journaller: Journaller = Journaller.empty,
    snapshotter: Snapshotter[S] = Snapshotter.empty): TestPersistentActor[S, SS, C, E] = {

    new TestPersistentActor(setup, journaller, snapshotter, Unapply.of[S], Unapply.of[C], Unapply.of[E])
  }
}
