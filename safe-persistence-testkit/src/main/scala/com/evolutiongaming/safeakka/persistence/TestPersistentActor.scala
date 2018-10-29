package com.evolutiongaming.safeakka.persistence

import com.evolutiongaming.safeakka.actor.{Sender, Unapply, WithSender}

import scala.collection.immutable.Seq


class TestPersistentActor[S, SS, C, E](
  val setupPersistentActor: SetupPersistentActor[S, SS, C, E],
  val journaller: Journaller,
  val snapshotter: Snapshotter[S],
  val asS: Unapply[S],
  val asC: Unapply[C],
  val asE: Unapply[E]) extends SafePersistentActor.PersistentLike[S, SS, C, E] {

  private var seqNr: SeqNr = 0L
  private var stash: List[WithSender[Any]] = Nil
  private var persistCallback: Option[() => Unit] = None

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
      val sender = withSender.sender getOrElse context.system.deadLetters
      withSender.msg match {
        case asC(cmd) => onCmd(PersistenceSignal.Cmd(cmd, sender), seqNr)
        case msg      => onAny(msg, seqNr, sender)
      }
    }
    stash = Nil

    context.become(rcvCommand)
  }

  override protected def onCmd(signal: PersistenceSignal[C], seqNr: SeqNr): Unit = {
    super.onCmd(signal, seqNr)
    persistCallback foreach { _.apply() }
    persistCallback = None
  }


  override protected def onAny(msg: Any, seqNr: SeqNr, sender: Sender): Unit = {
    super.onAny(msg, seqNr, sender)
    persistCallback foreach { _.apply() }
    persistCallback = None
  }

  def persistEvents(events: Seq[E])(handler: E => Unit): Unit = {
    val persist = () => for {event <- events} {
      seqNr += 1
      handler(event)
    }
    persistCallback = Some(persist)
  }

  def lastSeqNr() = seqNr

  override def toString = s"TestPersistentActor($persistenceId)"
}

object TestPersistentActor {

  def apply[S, SS, C, E](
    setup: SetupPersistentActor[S, SS, C, E],
    journaller: Journaller,
    snapshotter: Snapshotter[S])(implicit
    asS: Unapply[S],
    asC: Unapply[C],
    asE: Unapply[E]): TestPersistentActor[S, SS, C, E] = {

    new TestPersistentActor(setup, journaller, snapshotter, asS, asC, asE)
  }
}
