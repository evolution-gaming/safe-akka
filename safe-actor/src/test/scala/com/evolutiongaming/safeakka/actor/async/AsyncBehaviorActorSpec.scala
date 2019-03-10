package com.evolutiongaming.safeakka.actor.async

import akka.actor.{ActorIdentity, Identify}
import com.evolutiongaming.concurrent.CurrentThreadExecutionContext
import com.evolutiongaming.safeakka.actor._
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import org.scalatest.WordSpec

import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace

class AsyncBehaviorActorSpec extends WordSpec with ActorSpec {

  sealed trait M

  object M {
    case class Inc(idx: Int) extends M
    case class IncAsync(idx: Int, f1: Future[Unit], f2: Future[Unit]) extends M
    case class Stop(idx: Int) extends M
    case class StopAsync(idx: Int, f1: Future[Unit], f2: Future[Unit]) extends M
    case class Fail1(idx: Int) extends M
    case class Fail2(idx: Int, f: Future[Unit]) extends M
    case class Fail3(idx: Int, f: Future[Unit]) extends M
    case class Fail4(idx: Int, f1: Future[Unit], f2: Future[Unit]) extends M
  }

  type S = List[Int]

  "AsyncBehavior" should {

    "handle msg immediately" in new Scope {
      ref ! M.Inc(0)
      expectMsg(List(0))
    }

    "asynchronously get handler and immediately handle msg" in new Scope {
      val p = Promise[Unit]
      ref ! M.IncAsync(0, p.future, successful)
      flush()
      p.success(())
      expectMsg(List(0))
    }

    "asynchronously get handler and asynchronously handle msg" in new Scope {
      val p0 = Promise[Unit]
      val p1 = Promise[Unit]
      ref ! M.IncAsync(0, p0.future, p1.future)
      flush()
      p0.success(())
      flush()
      p1.success(())
      expectMsg(List(0))
    }

    "immediately get handler and asynchronously handle msg" in new Scope {
      val p = Promise[Unit]
      ref ! M.IncAsync(0, successful, p.future)
      flush()
      p.success(())
      expectMsg(List(0))
    }

    "preserve order" in new Scope {
      val p0 = Promise[Unit]
      val p1 = Promise[Unit]
      val p7 = Promise[Unit]
      val p8 = Promise[Unit]

      val msgs = List(
        M.IncAsync(0, p0.future, p1.future),
        M.Inc(1),
        M.IncAsync(2, successful, successful),
        M.Fail1(3),
        M.Fail2(4, successful),
        M.Fail3(5, successful),
        M.Fail4(6, successful, successful),
        M.IncAsync(7, successful, p7.future),
        M.IncAsync(8, p8.future, successful),
        M.StopAsync(9, successful, successful),
        M.Inc(10))

      msgs foreach { ref ! _ }

      expectMsg(TestException(3))
      expectMsg(TestException(4))

      flush()

      p0.success(())

      flush()

      p1.success(())

      expectMsg(List(0))
      expectMsg(List(1, 0))
      expectMsg(List(2, 1, 0))

      expectMsg(TestException(5))
      expectMsg(TestException(6))

      flush()
      p7.success(())
      expectMsg(List(7, 2, 1, 0))

      flush()
      p8.success(())
      expectMsg(List(8, 7, 2, 1, 0))
      expectMsg(List(9, 8, 7, 2, 1, 0))

      expectMsg(Stopped(List(Signal.Msg(M.Inc(10), testActor))))
    }

    "handle msg immediately and then stop" in new Scope {
      ref ! M.Stop(0)
      expectMsg(List(0))
      expectMsg(Stopped())
    }

    "asynchronously get handler and immediately handle msg and then stop" in new Scope {
      val p = Promise[Unit]
      ref ! M.StopAsync(0, p.future, successful)
      p.success(())
      expectMsg(List(0))
      expectMsg(Stopped())
    }

    "asynchronously get handler and asynchronously handle msg and then stop" in new Scope {
      val p0 = Promise[Unit]
      val p1 = Promise[Unit]
      ref ! M.StopAsync(0, p0.future, p1.future)
      p0.success(())
      p1.success(())
      expectMsg(List(0))
      expectMsg(Stopped())
    }

    "immediately get handler and asynchronously handle msg and then stop" in new Scope {
      val p0 = Promise[Unit]
      ref ! M.StopAsync(0, successful, p0.future)
      p0.success(())
      expectMsg(List(0))
      expectMsg(Stopped())
    }

    "immediately get handler and asynchronously handle msg and then stop and not loose enqueued msgs" in new Scope {
      val p = Promise[Unit]
      ref ! M.StopAsync(0, successful, p.future)
      val msgs = List[M](
        M.Inc(1),
        M.IncAsync(2, successful, successful),
        M.Stop(3),
        M.StopAsync(4, successful, successful),
        M.Inc(5))
      msgs foreach { ref ! _ }
      flush()
      p.success(())
      expectMsg(List(0))
      val signals = msgs map { msg => Signal.Msg(msg, testActor) }
      expectMsg(Stopped(signals))
    }

    "asynchronously get handler and immediately handle msg and then stop without loose enqueued msgs" in new Scope {
      val p = Promise[Unit]
      ref ! M.StopAsync(0, p.future, successful)
      val msgs = List[M](
        M.Inc(1),
        M.IncAsync(2, successful, successful),
        M.Stop(3),
        M.StopAsync(4, successful, successful),
        M.Inc(5))
      msgs foreach { ref ! _ }

      flush()

      p.success(())
      expectMsg(List(0))
      val signals = msgs map { msg => Signal.Msg(msg, testActor) }
      expectMsg(Stopped(signals))
    }

    "asynchronously get handler and asynchronously handle msg and then stop without loose enqueued msgs" in new Scope {
      val p0 = Promise[Unit]
      val p1 = Promise[Unit]
      ref ! M.StopAsync(0, p0.future, p1.future)

      val msg1 = List[M](
        M.Inc(1),
        M.IncAsync(2, successful, successful),
        M.Stop(3),
        M.StopAsync(4, successful, successful),
        M.Inc(5))
      msg1 foreach { ref ! _ }

      p0.success(())

      val msgs2 = List[M](
        M.Inc(1),
        M.IncAsync(2, successful, successful),
        M.Stop(3),
        M.StopAsync(4, successful, successful),
        M.Inc(5))
      msgs2 foreach { ref ! _ }

      flush()

      p1.success(())
      expectMsg(List(0))

      val signals = (msg1 ++ msgs2) map { msg => Signal.Msg(msg, testActor) }
      expectMsg(Stopped(signals))
    }

    "immediately get handler and asynchronously handle msg and then stop without loose enqueued msgs" in new Scope {
      val p = Promise[Unit]
      ref ! M.StopAsync(0, successful, p.future)
      val msgs = List[M](
        M.Inc(1),
        M.IncAsync(2, successful, successful),
        M.Stop(3),
        M.StopAsync(4, successful, successful),
        M.Inc(5))
      msgs foreach { ref ! _ }

      flush()

      p.success(())
      expectMsg(List(0))
      val signals = msgs map { msg => Signal.Msg(msg, testActor) }
      expectMsg(Stopped(signals))
    }

    "handle msg immediately and not fail" in new Scope {
      ref ! M.Fail1(0)
      expectMsg(TestException(0))

      ref ! M.IncAsync(1, failed(1), successful)
      expectMsg(TestException(1))

      ref ! M.IncAsync(2, successful, failed(2))
      expectMsg(TestException(2))

      ref ! M.Fail2(3, successful)
      expectMsg(TestException(3))

      ref ! M.Fail3(4, successful)
      expectMsg(TestException(4))

      ref ! M.Fail4(5, successful, successful)
      expectMsg(TestException(5))

      ref ! M.Inc(6)
      expectMsg(List(6))
    }

    "asynchronously get handler and immediately handle msg and not fail" in new Scope {
      val p0 = Promise[Unit]
      ref ! M.IncAsync(0, p0.future, successful)
      flush()
      p0.failure(TestException(0))
      expectMsg(TestException(0))

      val p1 = Promise[Unit]
      ref ! M.IncAsync(1, p1.future, successful)
      flush()
      p1.failure(TestException(1))
      expectMsg(TestException(1))

      val p2 = Promise[Unit]
      ref ! M.IncAsync(2, p2.future, failed(2))
      flush()
      p2.success(())
      expectMsg(TestException(2))

      val p3 = Promise[Unit]
      ref ! M.Fail2(3, p3.future)
      flush()
      p3.success(())
      expectMsg(TestException(3))

      val p4 = Promise[Unit]
      ref ! M.Fail3(4, p4.future)
      flush()
      p4.success(())
      expectMsg(TestException(4))

      val p5 = Promise[Unit]
      ref ! M.Fail4(5, p5.future, successful)
      flush()
      p5.success(())
      expectMsg(TestException(5))

      ref ! M.Inc(6)
      expectMsg(List(6))
    }

    "asynchronously get handler and asynchronously handle msg and not fail" in new Scope {
      val p0 = Promise[Unit]
      val p1 = Promise[Unit]
      ref ! M.IncAsync(0, p0.future, p1.future)
      flush()
      p0.success(())
      flush()
      p1.failure(TestException(0))
      expectMsg(TestException(0))

      ref ! M.Inc(1)
      expectMsg(List(1))
    }

    "immediately get handler and asynchronously handle msg and not fail" in new Scope {
      val p0 = Promise[Unit]
      ref ! M.IncAsync(0, successful, p0.future)
      flush()
      p0.failure(TestException(0))
      expectMsg(TestException(0))

      ref ! M.Inc(1)
      expectMsg(List(1))
    }
  }

  //  val log = ActorLog(system, getClass)
  val log = ActorLog.empty.prefixed("AsyncBehaviorActorSpec")

  private trait Scope extends ActorScope {
    lazy val successful = Future.successful(())
    lazy val ref = newRef()

    def failed(idx: Int) = Future.failed(TestException(idx))

    def flush(): Unit = {
      ref ! Identify(())
      expectMsgType[ActorIdentity]
    }

    def onStop(dropped: List[Signal.Msg[M]]): Unit = {
      testActor ! Stopped(dropped)
    }

    def behavior(ctx: ActorCtx) = AsyncBehavior[S, M](ctx, log, Nil, onStop) { (state, signal) =>
      implicit val ec = CurrentThreadExecutionContext
      signal match {
        case Signal.Msg(msg, sender) => msg match {

          case M.Inc(idx) =>
            AsyncHandler now { (state: S) =>
              val newState = idx :: state
              sender ! newState
              newState
            }

          case M.IncAsync(idx, f1, f2) =>
            f1.failed foreach { failure => sender ! failure }
            f1 map { _ =>
              (state: S) =>
                f2.failed foreach { failure => sender ! failure }
                f2 map { _ =>
                  val newState = idx :: state
                  sender ! newState
                  Some(newState)
                }
            }

          case M.Stop(idx) =>
            AsyncHandler stop { (state: S) =>
              val newState = idx :: state
              sender ! newState
            }

          case M.StopAsync(idx, f1, f2) =>
            f1 map { _ =>
              (state: S) =>
                f2 map { _ =>
                  val newState = idx :: state
                  sender ! newState
                  None
                }
            }
          case M.Fail1(idx)             =>
            val exception = TestException(idx)
            sender ! exception
            throw exception

          case M.Fail2(idx, f) =>
            f map { _ =>
              val exception = TestException(idx)
              sender ! exception
              throw TestException(idx)
            }

          case M.Fail3(idx, f) =>
            f map { _ =>
              (_: S) => {
                val exception = TestException(idx)
                sender ! exception
                throw TestException(idx)
              }
            }

          case M.Fail4(idx, f1, f2) =>
            f1 map { _ =>
              (_: S) =>
                f2 map { _ =>
                  val exception = TestException(idx)
                  sender ! exception
                  throw TestException(idx)
                }
            }

        }

        case _: Signal.System => AsyncHandler.empty
      }
    }

    def newRef() = {
      val setup: SetupActor[M] = (ctx: ActorCtx) => {
        val log = ActorLog.empty.prefixed("AsyncBehaviorActorSpec")
        (behavior(ctx), log)
      }
      SafeActorRef(setup)
    }

    case class Stopped(dropped: List[Signal.Msg[M]] = Nil)
  }

  case class TestException(idx: Int) extends RuntimeException with NoStackTrace {
    override def toString: String = s"$productPrefix($idx)"
  }
}
