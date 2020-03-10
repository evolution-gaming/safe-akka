package com.evolutiongaming.safeakka.persistence.async

import java.util.UUID

import akka.actor.ActorRef
import cats.implicits._
import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog}
import com.evolutiongaming.safeakka.persistence._
import com.evolutiongaming.safeakka.persistence.async.AsyncPersistentBehavior.Metrics
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace
import scala.util.{Success, Try}

class AsyncPersistentBehaviorSpec extends AnyWordSpec with ActorSpec with Matchers {
  import AsyncPersistentBehaviorSpec._

  type S = Counter
  type C = Cmd
  type E = Event

  "Counter" should {

    "receive command and persist event" in new Scope {
      ref ! Cmd.Get
      expectMsg(Counter(0, 0L))
      ref ! Cmd(Event.Inc)
      expectMsg((Counter(1, 1L), 1L.pure[Try]))

      val ref2 = newRef
      ref2 ! Cmd.Get
      expectMsg(Counter(1, 1L))
    }

    "handle exception withing actor rcv in sync mode" in new Scope {
      ref ! Cmd.Async(Future.failed(TestException))
      ref ! Cmd(Event.Inc)
      expectMsg((Counter(1, 1L), 1L.pure[Try]))
    }

    "handle exception withing actor rcv in async mode" in new Scope {
      val promise = Promise[Nel[E]]
      ref ! Cmd(promise)
      expectNoMessage(100.millis)

      promise.failure(TestException)

      ref ! Cmd(Event.Inc)
      expectMsg((Counter(1, 1L), 1L.pure[Try]))
    }

    "preserve order" in new Scope {
      ref ! Cmd.Get
      expectMsg(Counter(0, 0L))

      ref ! Cmd(Event.Inc, Event.Dec, Event.Inc)
      expectMsg((Counter(1, 3L), 3L.pure[Try]))

      val promise1 = Promise[Nel[E]]
      val promise2 = Promise[Nel[E]]
      val promise3 = Promise[Nel[E]]

      ref ! Cmd(promise1)
      ref ! Cmd(Event.Inc)
      ref ! Cmd(Event.Dec)
      ref ! Cmd(Event.Inc)
      ref ! Cmd.Get
      ref ! Cmd(promise2)
      ref ! Cmd(Event.Dec, Event.Dec)
      ref ! Cmd.Get

      expectNoMessage(100.millis)
      promise1.success(Nel(Event.Dec))

      expectMsg((Counter(0, 4L), 4L.pure[Try]))
      expectMsg((Counter(1, 5L), 5L.pure[Try]))
      expectMsg((Counter(0, 6L), 6L.pure[Try]))
      expectMsg((Counter(1, 7L), 7L.pure[Try]))
      expectMsg(Counter(1, 7L))

      promise2.success(Nel(Event.Inc, Event.Dec, Event.Inc))
      expectMsg((Counter(2, 10L), 10L.pure[Try]))
      expectMsg((Counter(0, 12L), 12L.pure[Try]))
      expectMsg(Counter(0, 12L))

      ref ! Cmd.Get
      expectMsg(Counter(0, 12L))

      ref ! Cmd(promise3)
      ref ! Cmd.Get
      ref ! Cmd.Stop
      ref ! Cmd(Event.Inc)
      ref ! Cmd.Get
      expectNoMessage(100.millis)

      promise3.success(Nel(Event.Inc))
      expectMsg((Counter(1, 13L), 13L.pure[Try]))
      expectMsg(Counter(1, 13L))
      expectMsg(Stopped(Counter(1, 13L)))
      expectMsg(Dropped(Nel(Cmd(Event.Inc), Cmd.Get)))
    }

    "batch event persistence in case of slow journal" in new Scope { scope =>
      val settings = QueueGraph.Settings.Default.copy(batchSize = 2, onBatchParallelism = 1)
      val asyncPersistenceBehavior = {
        import system.dispatcher
        new AsyncPersistentBehavior[S, C, E](
          testActor.tell(_, ActorRef.noSender),
          settings,
          ActorLog.empty,
          asyncSignalHandler,
          Metrics.Empty,
        )
      }
      def completeHandlers(handlers: asyncPersistenceBehavior.Handlers) =
        handlers.promise.complete(Success(()))
      val PersistentBehavior.Rcv(onSignal0, _) = asyncPersistenceBehavior(Counter(0, 0), 0)
      val PersistentBehavior.Rcv(onSignal1, _) = onSignal0(
        PersistenceSignal.Cmd(Cmd(Event.Inc), ActorRef.noSender),
        0L,
      )
      val handlers1 = expectMsgType[asyncPersistenceBehavior.Handlers]
      handlers1.values.size shouldBe 1
      completeHandlers(handlers1)

      val PersistentBehavior.Rcv(onSignal2, _) = onSignal1(
        PersistenceSignal.Cmd(Cmd(Event.Dec), ActorRef.noSender),
        1L,
      )
      val handlers2 = expectMsgType[asyncPersistenceBehavior.Handlers]
      handlers2.values.size shouldBe 1

      val PersistentBehavior.Rcv(onSignal3, _) = onSignal2(
        PersistenceSignal.Cmd(Cmd(Event.Inc), ActorRef.noSender),
        2L,
      )
      val PersistentBehavior.Rcv(_, _) = onSignal3(
        PersistenceSignal.Cmd(Cmd(Event.Inc), ActorRef.noSender),
        2L,
      )
      completeHandlers(handlers2)
      val handlers4 = expectMsgType[asyncPersistenceBehavior.Handlers]
      completeHandlers(handlers4)
    }
  }

  private trait Scope extends ActorScope {

    val persistenceId = UUID.randomUUID().toString

    def asyncPersistenceSetup = new AsyncPersistenceSetup[S, S, C, E] {

      def persistenceId = Scope.this.persistenceId

      def log = ActorLog.empty

      def onRecoveryStarted(offer: Option[SnapshotOffer[S]], journaller: Journaller, snapshotter: Snapshotter[S]) =
        new Recovering {

          def state = offer map { _.snapshot } getOrElse Counter(0, 0)

          def eventHandler(state: S, event: E, seqNr: SeqNr) = state(event, seqNr)

          def onCompleted(state: S, seqNr: SeqNr) = asyncSignalHandler

          def onStopped(state: S, seqNr: SeqNr) = {}
        }

      def onStopped(seqNr: SeqNr) = {}
    }

    def asyncSignalHandler: AsyncSignalHandler[S, C, E] = new AsyncSignalHandler[S, C, E] {

      def handler(state: S, seqNr: SeqNr, signal: PersistenceSignal[C]): AsyncHandler[S, E] = signal match {

        case PersistenceSignal.Cmd(cmd, sender) =>
          def onGet = {
            val handler = (state: S, _: SeqNr) =>
              CmdResult.empty {
                sender.tell(state, ActorRef.noSender)
              }
            Future.successful(handler)
          }

          def onChange(events: Future[Nel[E]]) = {
            implicit val ec = CurrentThreadExecutionContext
            for {
              events <- events
            } yield (state: S, seqNr: SeqNr) => {
              val zero = (state, seqNr + 1)
              val (stateAfter, _) = events.foldLeft(zero) {
                case ((state, seqNr), event) =>
                  val stateAfter = state(event, seqNr)
                  val seqNrAfter = seqNr + 1
                  (stateAfter, seqNrAfter)
              }

              val records = events map { event =>
                Record(event)
              }
              val onPersisted = (seqNr: Try[SeqNr]) => sender.tell((stateAfter, seqNr), ActorRef.noSender)
              CmdResult.Change(stateAfter, records, onPersisted)
            }
          }

          def onStop = {
            val handler = (state: S, _: SeqNr) =>
              CmdResult.stop {
                sender.tell(Stopped(state), ActorRef.noSender)
              }
            Future.successful(handler)
          }

          cmd match {
            case Cmd.Async(handler) => onChange(handler)
            case Cmd.Sync(handler)  => onChange(Future.successful(handler))
            case Cmd.Stop           => onStop
            case Cmd.Get            => onGet
          }

        case signal =>
          val handler = (_: S, _: SeqNr) =>
            CmdResult.empty {
              testActor.tell(signal, ActorRef.noSender)
            }
          Future.successful(handler)
      }

      def onDropped(signals: Nel[PersistenceSignal[C]]) = {
        val cmds = signals.collect { case signal: PersistenceSignal.Cmd[C] => signal.cmd }
        Nel.opt(cmds).foreach { cmds =>
          testActor.tell(Dropped(cmds), ActorRef.noSender)
        }
      }
    }

    def persistenceSetup(ctx: ActorCtx) = PersistenceSetupFromAsync(asyncPersistenceSetup, ctx)

    def newRef = PersistentActorRef(persistenceSetup)

    val ref = newRef
  }

  sealed trait Cmd

  object Cmd {

    def apply(event: Event, events: Event*): Cmd = Sync(Nel(event, events.toList))

    def apply(promise: Promise[Nel[Event]]): Cmd = Async(promise.future)

    case object Get extends Cmd
    case object Stop extends Cmd
    case class Async(events: Future[Nel[E]]) extends Cmd
    case class Sync(events: Nel[E]) extends Cmd
  }

  case class Stopped(counter: Counter)
  case class Dropped(cmds: Nel[Cmd])

  case object TestException extends RuntimeException with NoStackTrace
}

object AsyncPersistentBehaviorSpec {
  final case class Counter(value: Int, seqNr: SeqNr) {
    def apply(event: Event, seqNr: SeqNr): Counter = {
      val x = event match {
        case Event.Inc => 1
        case Event.Dec => -1
      }
      Counter(value + x, seqNr)
    }
  }

  sealed trait Event

  object Event {
    case object Inc extends Event
    case object Dec extends Event
  }
}
