package com.evolutiongaming.safeakka.persistence

import com.evolutiongaming.safeakka.actor.{Sender, Unapply, WithSender}

import scala.collection.immutable.Seq


class TestPersistentActor[S, SS, C, E](
  val setup: SetupPersistentActor[S, SS, C, E],
  eventsourced: Eventsourced,
  snapshotter: Snapshotter[S],
  val asS: Unapply[S],
  val asC: Unapply[C],
  val asE: Unapply[E]) extends SafePersistent[S, SS, C, E] {

  private var seqNr: SeqNr = 0L
  private var stash: List[WithSender[Any]] = Nil
  private var persistCallback: Option[() => Unit] = None

  def ctx = PersistentActorCtx(context, eventsourced, snapshotter)

  def receive = rcvRecover orElse {
    case msg => stash = WithSender(msg, sender()) :: stash
  }

  override protected def onSnapshotOffer(snapshot: SnapshotOffer[S]): Unit = {
    seqNr = snapshot.metadata.sequenceNr
    super.onSnapshotOffer(snapshot)
  }

  override protected def onEvent(event: WithNr[E]): Unit = {
    val incremented = event.inc
    seqNr = incremented.seqNr
    super.onEvent(incremented)
  }

  override protected def onRecoveryCompleted(): Unit = {
    super.onRecoveryCompleted()

    stash.foldRight(()) { (withSender, _) =>
      val sender = withSender.sender getOrElse context.system.deadLetters
      withSender.msg match {
        case asC(cmd) => onCmd(PersistenceSignal.Cmd(cmd), sender)
        case msg      => onAny(msg, sender)
      }
    }
    stash = Nil

    context.become(rcvCommand)
  }

  override protected def onCmd(signal: PersistenceSignal[C], sender: Sender): Unit = {
    super.onCmd(signal, sender)
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

  def lastSeqNr = seqNr

  override def toString = s"TestPersistentActor($persistenceId)"
}

object TestPersistentActor {

  def apply[S, SS, C, E](
    setup: SetupPersistentActor[S, SS, C, E],
    eventsourced: Eventsourced,
    snapshotter: Snapshotter[S])(implicit
    asS: Unapply[S],
    asC: Unapply[C],
    asE: Unapply[E]): SafePersistent[S, SS, C, E] = {

    new TestPersistentActor(setup, eventsourced, snapshotter, asS, asC, asE)
  }
}
