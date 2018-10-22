package com.evolutiongaming.safeakka.actor.async

import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.safeakka.actor._

import scala.collection.immutable.Queue
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

    type Q = Queue[SignalAndHandler]

    final case class SignalAndHandler(signal: Signal[M], asyncHandler: AsyncHandler[S])
    final case class HandlerMsg(current: SignalAndHandler, handler: Try[Handler[S]])
    final case class NewStateMsg(current: SignalAndHandler, newState: Try[Option[S]])

    implicit val ec = CurrentThreadExecutionContext

    def safe[A](f: => Future[A]) = {
      try f catch { case NonFatal(failure) => Future.failed(failure) }
    }

    def pipeToSelf[A](x: Future[A])(f: Try[A] => Any): Unit = {
      x onComplete { x => ctx.self.tell(f(x), ctx.self) }
    }

    def onSuccess[A](x: Try[A], current: SignalAndHandler, state: S, queue: Q)(f: A => Behavior[M]): Behavior[M] = {
      x match {
        case Success(x)       => f(x)
        case Failure(failure) =>
          val signal = current.signal
          log.error(s"failed to process $signal in $state $failure", failure)
          nextHandler(state, queue)
      }
    }

    def onAsyncHandler(current: SignalAndHandler, state: S, queue: Q): Behavior[M] = {
      val asyncHandler = current.asyncHandler
      asyncHandler.value match {
        case Some(handler) => onHandler(current, state, handler, queue)
        case None          =>
          pipeToSelf(asyncHandler) { handler => HandlerMsg(current, handler) }
          rcv(state, current +: queue)
      }
    }

    def onHandler(current: SignalAndHandler, state: S, handler: Try[Handler[S]], queue: Q): Behavior[M] = {
      onSuccess(handler, current, state, queue) { handler =>
        val newState = safe { handler(state) }
        newState.value match {
          case Some(newState) => onState(current, state, newState, queue)
          case None           =>
            pipeToSelf(newState) { newState => NewStateMsg(current, newState) }
            rcv(state, current +: queue)
        }
      }
    }

    def onState(current: SignalAndHandler, state: S, newState: Try[Option[S]], queue: Q): Behavior[M] = {
      onSuccess(newState, current, state, queue) {
        case Some(newState) => nextHandler(newState, queue)
        case None           =>
          val signals = queue collect { case SignalAndHandler(signal: Signal.Msg[M], _) => signal }
          onStop(signals.toList)
          Behavior.stop
      }
    }

    def nextHandler(state: S, queue: Q): Behavior[M] = {
      queue.dequeueOption match {
        case Some((elem, queue)) => onAsyncHandler(elem, state, queue)
        case None                => rcv(state, queue)
      }
    }

    def rcv(state: S, queue: Q): Behavior[M] = {
      val onSignal: OnSignal[M] = signal => {
        val asyncHandler = safe { f(state, signal) }
        if (queue.isEmpty) {
          onAsyncHandler(SignalAndHandler(signal, asyncHandler), state, queue)
        } else {
          rcv(state, queue enqueue SignalAndHandler(signal, asyncHandler))
        }
      }

      val onAny: OnAny[M] = {
        case HandlerMsg(signal, value)  => (_: Sender) => onHandler(signal, state, value, queue.tail)
        case NewStateMsg(signal, value) => (_: Sender) => onState(signal, state, value, queue.tail)
      }

      Behavior.Rcv(onSignal, onAny)
    }

    rcv(initial, Queue.empty)
  }
}
