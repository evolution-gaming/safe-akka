package com.evolutiongaming.safeakka.persistence

import scala.collection.immutable.Seq


sealed trait PersistentBehavior[-C, +E] {
  def compose[CC](f: CC => C): PersistentBehavior[CC, E]
  def rcvUnsafe[CC <: C, EE >: E](onAny: OnAny[CC, EE]): PersistentBehavior[CC, EE]
}

object PersistentBehavior {

  def stop[C, E]: PersistentBehavior[C, E] = Stop

  def apply[C, E](onSignal: OnSignal[C, E]): PersistentBehavior[C, E] = Rcv(onSignal)

  def persist[C, E](events: Seq[Record[E]])(onPersisted: SeqNr => PersistentBehavior[C, E]): PersistentBehavior[C, E] = {
    Persist(events, onPersisted)
  }

  def persist[C, E](event: Record[E], events: Record[E]*)(onPersisted: SeqNr => PersistentBehavior[C, E]): PersistentBehavior[C, E] = {
    Persist(event :: events.toList, onPersisted)
  }



  case class Rcv[-C, +E](
    onSignal: OnSignal[C, E],
    onAny: OnAny[C, E] = PartialFunction.empty) extends PersistentBehavior[C, E] {

    def compose[CC](f: CC => C): Rcv[CC, E] = Rcv[CC, E] { signal =>
      val result = signal.map { _.map(_.map(f)) }
      onSignal(result).compose(f)
    }

    def rcvUnsafe[CC <: C, EE >: E](onAny: OnAny[CC, EE]): Rcv[CC, EE] = copy(onAny = onAny)
  }



  case object Stop extends PersistentBehavior[Any, Nothing] {

    def compose[CC](f: CC => Any): Stop.type = this

    def rcvUnsafe[CC <: Any, EE >: Nothing](onAny: OnAny[CC, EE]): Stop.type = Stop
  }



  case class Persist[-C, +E](
    events: Seq[Record[E]],
    onPersisted: SeqNr => PersistentBehavior[C, E]) extends PersistentBehavior[C, E] {
    
    def compose[CC](f: CC => C): Persist[CC, E] = Persist[CC, E](events, onPersisted.andThen(_.compose(f)))

    def rcvUnsafe[CC <: C, EE >: E](onAny: OnAny[CC, EE]): Persist[CC, EE] = this
  }
}


case class Record[+E](event: E, onPersisted: Callback[SeqNr] = Callback.empty)

object Record {

  def of[E](event: E)(onPersisted: Callback[SeqNr]): Record[E] = Record(event, onPersisted)
}