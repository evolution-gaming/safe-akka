package com.evolutiongaming.safeakka.persistence

import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.Sender

import scala.util.Try


sealed trait PersistentBehavior[-C, +E] {

  def mapCommand[CC](f: CC => C): PersistentBehavior[CC, E]

  def mapEvent[EE](f: E => EE): PersistentBehavior[C, EE]

  def map[CC, EE](fc: CC => C, fe: E => EE): PersistentBehavior[CC, EE]
}

object PersistentBehavior {

  def stop[C, E]: PersistentBehavior[C, E] = Stop

  def apply[C, E](onSignal: OnSignal[C, E]): PersistentBehavior[C, E] = Rcv(onSignal)

  def persist[C, E](
    records: Nel[Record[E]],
    onPersisted: SeqNr => PersistentBehavior[C, E],
    onFailure: Throwable => Unit): PersistentBehavior[C, E] = {

    Persist(records, onPersisted, onFailure)
  }

  final case class Rcv[-C, +E](
    onSignal: OnSignal[C, E],
    onAny: OnAny[C, E] = OnAny.empty) extends PersistentBehavior[C, E] { self =>

    def mapCommand[CC](f: CC => C): Rcv[CC, E] = {
      val onSignal = (signal: PersistenceSignal[CC], seqNr: SeqNr) => {
        val behavior = self.onSignal(signal.map(f), seqNr)
        behavior.mapCommand(f)
      }

      val onAny = (seqNr: SeqNr, sender: Sender) => {
        self.onAny(seqNr, sender).andThen(_.mapCommand(f))
      }

      copy(onSignal = onSignal, onAny = onAny)
    }

    def mapEvent[EE](f: E => EE): Rcv[C, EE] = {
      val onSignal = (signal: PersistenceSignal[C], seqNr: SeqNr) => {
        val behavior = self.onSignal(signal, seqNr)
        behavior.mapEvent(f)
      }

      val onAny = (seqNr: SeqNr, sender: Sender) => {
        self.onAny(seqNr, sender).andThen(_.mapEvent(f))
      }
      copy(onSignal = onSignal, onAny = onAny)
    }

    def map[CC, EE](fc: CC => C, fe: E => EE): Rcv[CC, EE] = {

      val onSignal = (signal: PersistenceSignal[CC], seqNr: SeqNr) => {
        val behavior = self.onSignal(signal.map(fc), seqNr)
        behavior.map(fc, fe)
      }

      val onAny = (seqNr: SeqNr, sender: Sender) => {
        self.onAny(seqNr, sender).andThen(_.map(fc, fe))
      }

      copy(onSignal = onSignal, onAny = onAny)
    }
  }


  case object Stop extends PersistentBehavior[Any, Nothing] { self =>

    def map[CC, EE](fc: CC => Any, fe: Nothing => EE): self.type = self

    def mapCommand[CC](f: CC => Any): self.type = self

    def mapEvent[EE](f: Nothing => EE): self.type = self
  }


  final case class Persist[-C, +E](
    records: Nel[Record[E]],
    onPersisted: SeqNr => PersistentBehavior[C, E],
    onFailure: Throwable => Unit) extends PersistentBehavior[C, E] {

    def mapCommand[CC](f: CC => C): Persist[CC, E] = {
      copy(onPersisted = onPersisted.andThen(_.mapCommand(f)))
    }

    def mapEvent[EE](f: E => EE): Persist[C, EE] = {
      copy(records = records.map(_.map(f)), onPersisted = onPersisted.andThen(_.mapEvent(f)))
    }

    def map[CC, EE](fc: CC => C, fe: E => EE): Persist[CC, EE] = {
      copy(records = records.map(_.map(fe)), onPersisted = onPersisted.andThen(_.map(fc, fe)))
    }
  }
}


final case class Record[+E](event: E, onPersisted: Callback[Try[SeqNr]] = Callback.empty) {

  def map[EE](f: E => EE): Record[EE] = copy(event = f(event))
}

object Record {

  def of[E](event: E)(onPersisted: Callback[Try[SeqNr]]): Record[E] = Record(event, onPersisted)
}