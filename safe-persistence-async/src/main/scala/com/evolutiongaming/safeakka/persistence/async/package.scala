package com.evolutiongaming.safeakka.persistence

import scala.concurrent.Future
import scala.util.Try

package object async {

  type Handler[S, +E] = (S, SeqNr) => CmdResult[S, E]

  object Handler {
    private val Empty = (_: Any, _: SeqNr) => CmdResult.empty[Nothing, Nothing]

    def empty[S, E]: Handler[S, E] = Empty

    def empty[S, E](callback: (S, SeqNr) => Unit): Handler[S, E] = { (state: S, seqNr: SeqNr) =>
      CmdResult.empty {
        callback(state, seqNr)
      }
    }
  }


  type AsyncHandler[S, +E] = Future[Handler[S, E]]

  object AsyncHandler {

    private val Empty = Future.successful((_: Any, _: SeqNr) => CmdResult.empty[Nothing, Nothing])
    

    def empty[S, E]: AsyncHandler[S, E] = Empty

    def empty[S, E](callback: (S, SeqNr) => Unit): AsyncHandler[S, E] = apply(Handler.empty(callback))

    def apply[S, E](handler: Handler[S, E]): AsyncHandler[S, E] = Future.successful(handler)
  }


  type OnPersisted = Try[SeqNr] => Unit
}
