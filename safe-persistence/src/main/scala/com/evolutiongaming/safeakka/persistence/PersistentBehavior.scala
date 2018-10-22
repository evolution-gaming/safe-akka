package com.evolutiongaming.safeakka.persistence

import com.evolutiongaming.safeakka.actor.{Sender, Signal}

import scala.collection.immutable.Seq


sealed trait PersistentBehavior[-C, +E] {

  @deprecated("use mapC instead", "1.8.1")
  final def compose[CC](f: CC => C): PersistentBehavior[CC, E] = mapC(f)

  def mapC[CC](f: CC => C): PersistentBehavior[CC, E]

  def mapE[EE](f: E => EE): PersistentBehavior[C, EE]

  def map[CC, EE](fc: CC => C, fe: E => EE): PersistentBehavior[CC, EE]
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


  final case class Rcv[-C, +E](
    onSignal: OnSignal[C, E],
    onAny: OnAny[C, E] = PartialFunction.empty) extends PersistentBehavior[C, E] { self =>

    def mapC[CC](f: CC => C): Rcv[CC, E] = {

      val onSignal = (signal: Signal[WithNr[PersistenceSignal[CC]]]) => {
        val behavior = self.onSignal(signal.map { _.map(_.map(f)) })
        behavior.mapC(f)
      }

      val onAny = self.onAny.andThen { rcv => (seqNr: SeqNr, sender: Sender) => rcv(seqNr, sender).mapC(f) }

      copy(onSignal = onSignal, onAny = onAny)
    }

    def mapE[EE](f: E => EE): Rcv[C, EE] = {
      val onSignal = self.onSignal.andThen(_.mapE(f))
      val onAny = self.onAny.andThen { rcv => (seqNr: SeqNr, sender: Sender) => rcv(seqNr, sender).mapE(f) }
      copy(onSignal = onSignal, onAny = onAny)
    }

    def map[CC, EE](fc: CC => C, fe: E => EE): Rcv[CC, EE] = {
      val onSignal = (signal: Signal[WithNr[PersistenceSignal[CC]]]) => {
        val behavior = self.onSignal(signal.map { _.map(_.map(fc)) })
        behavior.map(fc, fe)
      }

      val onAny = self.onAny.andThen { rcv => (seqNr: SeqNr, sender: Sender) => rcv(seqNr, sender).map(fc, fe) }

      copy(onSignal = onSignal, onAny = onAny)
    }
  }


  case object Stop extends PersistentBehavior[Any, Nothing] { self =>

    def map[CC, EE](fc: CC => Any, fe: Nothing => EE): self.type = self

    def mapC[CC](f: CC => Any): self.type = self

    def mapE[EE](f: Nothing => EE): self.type = self
  }


  final case class Persist[-C, +E](
    events: Seq[Record[E]],
    onPersisted: SeqNr => PersistentBehavior[C, E]) extends PersistentBehavior[C, E] {

    def mapC[CC](f: CC => C): Persist[CC, E] = {
      copy(onPersisted = onPersisted.andThen(_.mapC(f)))
    }

    def mapE[EE](f: E => EE): Persist[C, EE] = {
      copy(events = events.map(_.map(f)), onPersisted = onPersisted.andThen(_.mapE(f)))
    }

    def map[CC, EE](fc: CC => C, fe: E => EE): Persist[CC, EE] = {
      copy(events = events.map(_.map(fe)), onPersisted = onPersisted.andThen(_.map(fc, fe)))
    }
  }
}


final case class Record[+E](event: E, onPersisted: Callback[SeqNr] = Callback.empty) {

  def map[EE](f: E => EE): Record[EE] = copy(event = f(event))
}

object Record {

  def of[E](event: E)(onPersisted: Callback[SeqNr]): Record[E] = Record(event, onPersisted)
}