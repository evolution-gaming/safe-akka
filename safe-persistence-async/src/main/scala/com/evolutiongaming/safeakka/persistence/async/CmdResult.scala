package com.evolutiongaming.safeakka.persistence.async

import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.persistence.Record

import scala.util.Try

sealed trait CmdResult[+S, +E]

object CmdResult {

  def empty[S, E](callback: => Unit): CmdResult[S, E] = Empty(() => callback)

  def empty[S, E]: CmdResult[S, E] = Empty.Empty

  def stop[S, E](callback: => Unit): CmdResult[S, E] = Stop(() => callback)

  def stop[S, E]: CmdResult[S, E] = Stop.Empty

  def records[S, E](state: S, records: Nel[Record[E]])(onPersisted: Try[Unit] => Unit): CmdResult[S, E] = {
    Records(state, records, onPersisted)
  }

  def records[S, E](state: S, record: Record[E], records: Record[E]*)(
    onPersisted: Try[Unit] => Unit,
  ): CmdResult[S, E] = {
    Records(state, Nel(record, records.toList), onPersisted)
  }

  sealed trait NonStop[+S, +E] extends CmdResult[S, E]

  final case class Records[+S, +E](state: S, records: Nel[Record[E]], onPersisted: Try[Unit] => Unit)
      extends NonStop[S, E]

  final case class Empty(callback: () => Unit) extends NonStop[Nothing, Nothing]

  object Empty {
    val Empty: Empty = CmdResult.Empty(() => ())
  }

  final case class Stop(onStop: () => Unit) extends CmdResult[Nothing, Nothing]

  object Stop {
    val Empty: Stop = Stop(() => ())
  }

  implicit class CmdResultState[S, E](val self: CmdResult[S, E]) {
    def state: Option[S] = self match {
      case Records(state, _, _) => Some(state)
      case _                    => None
    }
  }
}
