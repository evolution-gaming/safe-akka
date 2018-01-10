package com.evolutiongaming.safeakka.actor

import akka.actor.{ActorIdentity, ActorRef, Identify, PoisonPill, ReceiveTimeout}
import akka.testkit.{TestActorRef, TestProbe}
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class SafeActorSpec extends WordSpec with ActorSpec with Matchers {

  "SafeActor" should {

    "proxy to behavior" in new Scope {
      ref ! 1
      expectMsg(receive(1))
      lastSender shouldEqual system.deadLetters

      ref ! "2"
      expectMsg(receive("2"))
      lastSender shouldEqual system.deadLetters

      ref ! Identify("id")
      expectMsg(ActorIdentity("id", Some(ref)))
      lastSender shouldEqual system.deadLetters

      ref ! ReceiveTimeout
      expectMsg(Signal.RcvTimeout)
      lastSender shouldEqual system.deadLetters

      expectMsg(Signal.RcvTimeout)
      lastSender shouldEqual system.deadLetters

      ref ! PoisonPill
      expectMsg(Signal.PostStop)
      lastSender shouldEqual system.deadLetters
    }

    "support watch" in new WatchScope {
      val probe = TestProbe()
      ref ! Cmd.Watch(probe.ref)
      expectMsg(Ok)
      system stop probe.ref

      expectMsg(Signal.Terminated(probe.ref))
    }

    "support unwatch" in new WatchScope {
      val probe = TestProbe()
      ref ! Cmd.Watch(probe.ref)
      expectMsg(Ok)
      ref ! Cmd.Unwatch(probe.ref)
      expectMsg(Ok)

      system stop probe.ref

      ref ! PoisonPill
      expectMsg(Signal.PostStop)
    }

    "stop actor on startup" in new ActorScope {
      def actor = SafeActor[Any] { ctx =>
        watch(ctx.self)
        (Behavior.stop, ActorLog.empty)
      }

      val ref = TestActorRef(actor)
      expectTerminated(ref)
    }

    "stop self" in new ActorScope {
      case object Stop

      val behavior = Behavior[Stop.type] {
        case Signal.Msg(Stop, _) => Behavior.stop
        case signal              =>
          testActor.tell(signal, ActorRef.noSender)
          Behavior.same
      }

      def actor = SafeActor[Stop.type](_ => (behavior, ActorLog.empty))
      val ref = TestActorRef(actor)
      ref ! Stop
      expectMsg(Signal.PostStop)
    }
  }

  private trait Scope extends ActorScope {

    val behavior = Behavior[Any] { signal =>
      testActor.tell(signal, ActorRef.noSender)
      Behavior.same
    }

    def actor = SafeActor[Any] { ctx =>
      ctx.setReceiveTimeout(100.millis)
      (behavior, ActorLog.empty)
    }

    lazy val ref = TestActorRef(actor)

    def receive(msg: Any) = Signal.Msg(msg, testActor)
  }

  private trait WatchScope extends ActorScope {

    val setup: SetupActor[Cmd] = ctx => {
      val behavior = Behavior.stateless[Cmd] {
        case Signal.Msg(cmd, sender)   =>
          cmd match {
            case Cmd.Watch(ref)   => ctx.watch(ref)
            case Cmd.Unwatch(ref) => ctx.unwatch(ref)
          }
          sender.tell(Ok, ActorRef.noSender)
        case signal: Signal.Terminated => testActor.tell(signal, ActorRef.noSender)
        case signal @ Signal.PostStop  => testActor.tell(signal, ActorRef.noSender)
        case _                         => ()
      }
      (behavior, ActorLog.empty)
    }

    val ref = SafeActorRef(setup)

    sealed trait Cmd

    object Cmd {
      case class Watch(ref: ActorRef) extends Cmd
      case class Unwatch(ref: ActorRef) extends Cmd
    }

    case object Ok
  }
}
