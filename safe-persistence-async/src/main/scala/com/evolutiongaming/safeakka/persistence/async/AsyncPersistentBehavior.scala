package com.evolutiongaming.safeakka.persistence.async

import akka.actor.{ActorRef, ClassicActorContextProvider, ClassicActorSystemProvider}
import akka.stream.{Materializer, QueueOfferResult, SystemMaterializer}
import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog, Sender, Signal}
import com.evolutiongaming.safeakka.persistence._
import com.evolutiongaming.util.TryHelper._

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[async] class AsyncPersistentBehavior[S, C, E](
  sendToSelf: Any => Unit,
  settings: QueueGraph.Settings,
  log: ActorLog,
  handler: AsyncSignalHandler[S, C, E],
  metrics: AsyncPersistentBehavior.Metrics,
)(
  implicit
  ec: ExecutionContext,
  materializer: Materializer,
) {

  import AsyncPersistentBehavior._

  type H = Handler[S, E]

  def apply(state: S, seqNr: SeqNr): PersistentBehavior.Rcv[C, E] = {

    def onBatch(hs: Nel[SignalAndHandler]): Future[Unit] = {
      metrics.onBatch(hs.size)
      val promise = Promise[Unit]
      sendToSelf(Handlers(hs, promise))
      promise.future
    }

    val queue = QueueGraph[SignalAndHandler](onBatch, settings).run()

    val enqueue = (elem: Elem) => {
      def errorMsg = s"failed to enqueue ${elem.signal}"

      val future = {
        implicit val ec = CurrentThreadExecutionContext
        elem.handler
          .map { x =>
            SignalAndHandler(elem.signal, Success(x))
          }
          .recover { case x => SignalAndHandler(elem.signal, Failure(x)) }
      }

      def onFailure(msg: => String, failure: Option[Throwable]): Unit = {
        elem.signal match {
          case PersistenceSignal.Sys(Signal.PostStop) =>
          case _ =>
            failure match {
              case None          => log.error(msg)
              case Some(failure) => log.error(s"$msg: $failure", failure)
            }
            sendToSelf(QueueFailure)
        }
      }

      (queue offer future) onComplete {
        case Success(QueueOfferResult.Enqueued)         =>
        case Success(QueueOfferResult.Failure(failure)) => onFailure(errorMsg, Some(failure))
        case Success(failure)                           => onFailure(s"$errorMsg: $failure", None)
        case Failure(failure)                           => onFailure(errorMsg, Some(failure))
      }
    }

    apply(state, seqNr, enqueue)
  }

  private def apply(initState: S, initSeqNr: SeqNr, enqueue: Callback[Elem]): PersistentBehavior.Rcv[C, E] = {

    def behavior(state: S, seqNr: SeqNr, queue: Queue[PersistenceSignal[C]]): PersistentBehavior.Rcv[C, E] = {

      def onHandlers(hs: Nel[SignalAndHandler], promise: Promise[Unit]) = {
        type R = CmdResult.NonStop[S, E]

        @tailrec
        def iterHandlers(
          state: S,
          seqNr: SeqNr,
          hs: List[SignalAndHandler],
          rs: List[R],
          size: Int,
        ): PersistentBehavior[C, E] = {

          def complete(behavior: => PersistentBehavior[C, E]) = {

            type FS = List[Try[Unit] => Unit]

            def run(fs: FS, result: Try[Unit]): Unit = fs.foldRight(()) { (f, _) =>
              f(result)
            }

            def onCompleted(fs: FS) = (_: SeqNr) => {
              promise.success(())
              run(fs, Try.unit)
              behavior
            }

            def onFailure(fs: FS) = (failure: Throwable) => {
              promise.success(())
              run(fs, Failure(failure))
            }

            @tailrec
            def iterRecords(rs: List[R], es: Nel[Nel[Record[E]]], fs: FS): PersistentBehavior[C, E] = rs match {
              case Nil =>
                val records = es.reverse.flattenNel
                PersistentBehavior.Persist(records, onCompleted(fs), onFailure(fs))

              case (r: CmdResult.Empty) :: rs =>
                val f = (_: Try[Unit]) => r.callback()
                iterRecords(rs, es, f :: fs)

              case (r: CmdResult.Records[S, E]) :: rs =>
                val records =
                  if (fs.isEmpty) r.records
                  else {
                    val head = r.records.head
                    val onPersisted = (seqNr: Try[SeqNr]) => {
                      run(fs, seqNr.unit)
                      head.onPersisted(seqNr)
                    }
                    Nel(head.copy(onPersisted = onPersisted), r.records.tail)
                  }
                iterRecords(rs, records :: es, r.onPersisted :: Nil)
            }

            @tailrec
            def iterEmpty(rs: List[R], fs: FS): PersistentBehavior[C, E] = rs match {
              case Nil =>
                onCompleted(fs)(seqNr)

              case (r: CmdResult.Empty) :: rs =>
                val f = (_: Try[Unit]) => r.callback()
                iterEmpty(rs, f :: fs)

              case (r: CmdResult.Records[S, E]) :: rs =>
                run(fs, Try.unit)
                iterRecords(rs, Nel(r.records), r.onPersisted :: Nil)
            }

            iterEmpty(rs.reverse, Nil)
          }

          hs match {
            case Nil => complete(behavior(state, seqNr, queue.drop(size)))
            case h :: hs =>
              h.handler match {
                case Success(h) =>
                  val result = h(state, seqNr)
                  result match {
                    case r: CmdResult.Records[S, E] =>
                      val newSeqNr = seqNr + r.records.size
                      val newState = r.state
                      iterHandlers(newState, newSeqNr, hs, r :: rs, size + 1)
                    case r: CmdResult.Empty => iterHandlers(state, seqNr, hs, r :: rs, size + 1)

                    case result: CmdResult.Stop =>
                      def behavior = {
                        result.onStop()
                        val left = queue.drop(size + 1).toList
                        Nel.opt(left) foreach handler.onDropped
                        PersistentBehavior.stop
                      }

                      complete(behavior)
                  }
                case Failure(failure) =>
                  log.error(s"failed to process ${h.signal} $failure", failure)
                  iterHandlers(state, seqNr, hs, rs, size + 1)
              }
          }
        }

        iterHandlers(state, seqNr, hs.toList, Nil, 0)
      }

      metrics.queueSize(queue.size)

      val onSignal: OnSignal[C, E] = (signal: PersistenceSignal[C], seqNr: SeqNr) => {

        val asyncHandler =
          try this.handler.handler(state, seqNr, signal)
          catch {
            case NonFatal(failure) => Future.failed(failure)
          }

        enqueue(Elem(signal, asyncHandler))
        behavior(state, seqNr, queue enqueue signal)
      }

      val onAny: OnAny[C, E] = (_: SeqNr, _: Sender) => {
        case Handlers(hs, promise) => onHandlers(hs, promise)
        case QueueFailure          => PersistentBehavior.stop
      }

      PersistentBehavior.Rcv(onSignal, onAny)
    }

    behavior(initState, initSeqNr, Queue.empty)
  }

  sealed trait Internal
  case class Elem(signal: PersistenceSignal[C], handler: Future[H])
  case class SignalAndHandler(signal: PersistenceSignal[C], handler: Try[H])
  case class Handlers(values: Nel[SignalAndHandler], promise: Promise[Unit]) extends Internal
  case object QueueFailure extends Internal
}

object AsyncPersistentBehavior {

  def apply[S, C, E](
    ctx: ActorCtx,
    log: ActorLog,
    state: S,
    seqNr: SeqNr,
    handler: AsyncSignalHandler[S, C, E],
    metrics: Metrics              = Metrics.Empty,
    settings: QueueGraph.Settings = QueueGraph.Settings.Default,
  ): PersistentBehavior.Rcv[C, E] = {
    import ctx.dispatcher
    implicit val materializer: Materializer = ctx.refFactory match {
      case a: ClassicActorContextProvider => Materializer(a)
      case a: ClassicActorSystemProvider  => Materializer(a)
      case _                              => SystemMaterializer(ctx.system).materializer
    }
    val behaviour =
      new AsyncPersistentBehavior[S, C, E](ctx.self.tell(_, ActorRef.noSender), settings, log, handler, metrics)
    behaviour(state, seqNr)
  }

  trait Metrics {
    def onBatch(size: Int): Unit
    def queueSize(size: Int): Unit
  }

  object Metrics {
    case object Empty extends Metrics {
      def onBatch(size: Int): Unit   = {}
      def queueSize(size: Int): Unit = {}
    }
  }

  implicit class NelOps[A](val self: Nel[Nel[A]]) extends AnyVal {
    def flattenNel: Nel[A] = self.flatMap(identity)
  }
}
