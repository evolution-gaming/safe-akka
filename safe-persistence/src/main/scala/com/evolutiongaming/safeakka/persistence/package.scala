package com.evolutiongaming.safeakka

import com.evolutiongaming.safeakka.actor.{Sender, Signal}

package object persistence {

  type SeqNr = Long

  type Timestamp = Long

  type EventHandler[S, E] = (S, WithNr[E]) => S

  type OnRecoveryStarted[-S, SS, C, E] = Option[SnapshotOffer[S]] => Recovering[SS, C, E]

  type OnRecoveryCompleted[-S, C, E] = WithNr[S] => PersistentBehavior[C, E]

  type SetupPersistentActor[S, SS, C, E] = PersistentActorCtx[S] => PersistenceSetup[S, SS, C, E]

  type PSignal[+C] = Signal[WithNr[PersistenceSignal[C]]]

  type OnSignal[-C, +E] = PSignal[C] => PersistentBehavior[C, E]

  type OnAny[-C, +E] = PartialFunction[Any, (SeqNr, Sender) => PersistentBehavior[C, E]]

  type Callback[-A] = A => Unit

  object Callback {
    def empty[A]: Callback[A] = Empty

    private object Empty extends Callback[Any] {def apply(x: Any): Unit = () }
  }
}