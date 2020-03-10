package com.evolutiongaming.safeakka.persistence.async

import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.persistence.Record

import scala.util.Try

sealed trait CmdResult[+S, +E]

object CmdResult {

  def empty[S, E](callback: => Unit): CmdResult[S, E] = Empty(() => callback)

  def empty[S, E]: CmdResult[S, E] = Empty.empty


  def stop[S, E](callback: => Unit): CmdResult[S, E] = Stop(() => callback)

  def stop[S, E]: CmdResult[S, E] = Stop.empty


  def change[S, E](
    state: S,
    records: Nel[Record[E]])(
    onPersisted: OnPersisted
  ): CmdResult[S, E] = {
    Change(state, records, onPersisted)
  }

  def change[S, E](
    state: S,
    record: Record[E],
    records: Record[E]*)(
    onPersisted: OnPersisted
  ): CmdResult[S, E] = {
    Change(state, Nel(record, records.toList), onPersisted)
  }


  @deprecated("use `change` instead", "3.0.0")
  def records[S, E](
    state: S,
    records: Nel[Record[E]])(
    onPersisted: Try[Unit] => Unit
  ): CmdResult[S, E] = {
    Records(state, records, onPersisted)
  }

  @deprecated("use `change` instead", "3.0.0")
  def records[S, E](
    state: S,
    record: Record[E],
    records: Record[E]*)(
    onPersisted: Try[Unit] => Unit
  ): CmdResult[S, E] = {
    Records(state, Nel(record, records.toList), onPersisted)
  }


  sealed trait NonStop[+S, +E] extends CmdResult[S, E]


  final case class Change[+S, +E](
    state: S,
    records: Nel[Record[E]],
    onPersisted: OnPersisted
  ) extends NonStop[S, E]


  @deprecated("use `Change` instead", "3.0.0")
  final case class Records[+S, +E](
    state: S,
    records: Nel[Record[E]],
    onPersisted: Try[Unit] => Unit
  ) extends NonStop[S, E]


  final case class Empty(callback: () => Unit) extends NonStop[Nothing, Nothing]

  object Empty {

    val empty: Empty = CmdResult.Empty(() => ())

    val Empty: Empty = empty
  }


  final case class Stop(onStop: () => Unit) extends CmdResult[Nothing, Nothing]

  object Stop {

    val empty: Stop = Stop(() => ())

    val Empty: Stop = empty
  }

  implicit class CmdResultState[S, E](val self: CmdResult[S, E]) {

    def state: Option[S] = self match {
      case Change(state, _, _) => Some(state)
      case _                   => None
    }
  }
}
