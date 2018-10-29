package com.evolutiongaming.safeakka

import com.evolutiongaming.safeakka.actor.{ActorCtx, Sender}

package object persistence {

  type SeqNr = Long

  type Timestamp = Long

  type EventHandler[S, E] = (S, E, SeqNr) => S

  type SetupPersistentActor[S, SS, C, E] = ActorCtx => PersistenceSetup[S, SS, C, E]


  type OnSignal[-C, +E] = (PersistenceSignal[C], SeqNr) => PersistentBehavior[C, E]


  type OnAny[-C, +E] = (SeqNr, Sender) => PartialFunction[Any, PersistentBehavior[C, E]]

  object OnAny {

    private val Empty: OnAny[Any, Nothing] = (_: SeqNr, _: Sender) => PartialFunction.empty

    def empty[C, E]: OnAny[C, E] = Empty
  }


  type Callback[-A] = A => Unit

  object Callback {
    def empty[A]: Callback[A] = Empty

    private object Empty extends Callback[Any] {def apply(x: Any): Unit = () }
  }
}