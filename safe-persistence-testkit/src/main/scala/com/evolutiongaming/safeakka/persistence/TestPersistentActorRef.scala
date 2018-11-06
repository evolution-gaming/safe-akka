package com.evolutiongaming.safeakka.persistence

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.{persistence => ap}
import com.evolutiongaming.safeakka.actor.SafeActorRef.CanRcv
import com.evolutiongaming.safeakka.actor.{SafeActorRef, Unapply}

trait TestPersistentActorRef[S, -C, E] extends SafeActorRef[C] {

  def recover(offer: Option[SnapshotOffer[S]] = None, events: Iterable[E] = Nil): Unit

  def recoverEvents(event: E, events: E*): Unit = recover(None, event :: events.toList)
}

object TestPersistentActorRef {

  def apply[S, SS, C, E](
    setup: SetupPersistentActor[S, SS, C, E],
    journaller: Journaller = Journaller.empty,
    snapshotter: Snapshotter[S] = Snapshotter.empty,
    name: Option[String] = None)
    (implicit system: ActorSystem, asS: Unapply[S], asC: Unapply[C], asE: Unapply[E]): TestPersistentActorRef[S, C, E] = {

    val props = Props(TestPersistentActor(setup, journaller, snapshotter))
    val ref = name map { name => system.actorOf(props, name) } getOrElse system.actorOf(props)
    new Impl[S, C, E](SafeActorRef(ref))
  }

  private class Impl[S, -C, E](ref: SafeActorRef[C]) extends TestPersistentActorRef[S, C, E] {

    def path = ref.path

    def unsafe = ref.unsafe

    def tell[TT](msg: TT, sender: Option[ActorRef])(implicit canRcv: CanRcv[TT, C]) = {
      ref.tell(msg, sender)(canRcv)
    }

    def recover(offer: Option[SnapshotOffer[S]], events: Iterable[E]): Unit = {
      offer foreach { offer =>
        val offerAny = ap.SnapshotOffer(offer.metadata, offer.snapshot)
        unsafe.tell(offerAny, ActorRef.noSender)
      }
      events foreach { event =>
        unsafe.tell(event, ActorRef.noSender)
      }
      unsafe.tell(ap.RecoveryCompleted, ActorRef.noSender)
    }
  }
}