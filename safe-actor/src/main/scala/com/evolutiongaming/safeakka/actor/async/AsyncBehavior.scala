package com.evolutiongaming.safeakka.actor.async

import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.safeakka.actor._

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


object AsyncBehavior {

  def apply[S, M](
    ctx: ActorCtx,
    log: ActorLog,
    initial: S,
    onStop: List[Signal.Msg[M]] => Unit)(
    f: (S, Signal[M]) => AsyncHandler[S]): Behavior[M] = {

    type Queue = List[SignalAndHandler]

    case class SignalAndHandler(signal: Signal[M], asyncHandler: AsyncHandler[S])
    case class HandlerMsg(current: SignalAndHandler, handler: Try[Handler[S]])
    case class NewStateMsg(current: SignalAndHandler, newState: Try[Option[S]])

    implicit val ec = CurrentThreadExecutionContext

    def safe[T](f: => Future[T]) = {
      try f catch { case NonFatal(failure) => Future.failed(failure) }
    }

    def pipeToSelf[T](x: Future[T])(f: Try[T] => Any): Unit = {
      x onComplete { x => ctx.self.tell(f(x), ctx.self) }
    }

    def onSuccess[T](x: Try[T], current: SignalAndHandler, state: S, queue: Queue)(f: T => Behavior[M]): Behavior[M] = {
      x match {
        case Success(x)       => f(x)
        case Failure(failure) =>
          val signal = current.signal
          log.error(s"failed to process $signal in $state $failure", failure)
          nextHandler(state, queue)
      }
    }

    def onAsyncHandler(current: SignalAndHandler, state: S, queue: Queue): Behavior[M] = {
      val asyncHandler = current.asyncHandler
      asyncHandler.value match {
        case Some(handler) => onHandler(current, state, handler, queue)
        case None          =>
          pipeToSelf(asyncHandler) { handler => HandlerMsg(current, handler) }
          rcv(state, current :: queue)
      }
    }

    def onHandler(current: SignalAndHandler, state: S, handler: Try[Handler[S]], queue: Queue): Behavior[M] = {
      onSuccess(handler, current, state, queue) { handler =>
        val newState = safe { handler(state) }
        newState.value match {
          case Some(newState) => onState(current, state, newState, queue)
          case None           =>
            pipeToSelf(newState) { newState => NewStateMsg(current, newState) }
            rcv(state, current :: queue)
        }
      }
    }

    def onState(current: SignalAndHandler, state: S, newState: Try[Option[S]], queue: Queue): Behavior[M] = {
      onSuccess(newState, current, state, queue) {
        case Some(newState) => nextHandler(newState, queue)
        case None           =>
          val signals = queue collect { case SignalAndHandler(signal: Signal.Msg[M], _) => signal }
          onStop(signals)
          Behavior.stop
      }
    }

    def nextHandler(state: S, queue: Queue): Behavior[M] = {
      queue match {
        case Nil           => rcv(state, Nil)
        case elem :: queue => onAsyncHandler(elem, state, queue)
      }
    }

    def rcv(state: S, queue: Queue): Behavior[M] = {
      val onSignal: OnSignal[M] = signal => {
        val asyncHandler = safe { f(state, signal) }
        if (queue.isEmpty) {
          onAsyncHandler(SignalAndHandler(signal, asyncHandler), state, queue)
        } else {
          rcv(state, queue :+ SignalAndHandler(signal, asyncHandler))
        }
      }

      val onAny: OnAny[M] = {
        case HandlerMsg(signal, value)  => (_: Sender) => onHandler(signal, state, value, queue.tail)
        case NewStateMsg(signal, value) => (_: Sender) => onState(signal, state, value, queue.tail)
      }

      Behavior.Rcv(onSignal, onAny)
    }

    rcv(initial, Nil)
  }
}
