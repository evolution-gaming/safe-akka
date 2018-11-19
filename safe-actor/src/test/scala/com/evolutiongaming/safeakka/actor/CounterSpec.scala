package com.evolutiongaming.safeakka.actor

import akka.actor.{PoisonPill, Props}
import akka.testkit.TestActorRef
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import org.scalatest.WordSpec

class CounterSpec extends WordSpec with ActorSpec {

  "Counter" should {

    "change counter" in new Scope {
      ref ! Msg.Inc
      expectMsg(1)
      ref ! Msg.Inc
      expectMsg(2)
      ref ! Msg.Dec
      expectMsg(1)
      ref ! Msg.Inc
      expectMsg(2)
      ref ! PoisonPill
    }
  }

  private trait Scope extends ActorScope {
    val props = Props(SafeActor[Msg](_ => (counter(0), ActorLog.empty)))
    val ref = SafeActorRef[Msg](TestActorRef(props))
  }

  private implicit val dummyMarshaller = Sender.TestIdentityMarshaller

  def counter(state: Int): Behavior[Msg] = Behavior.onMsg[Msg] {
    case Signal.Msg(msg, sender) =>
      val result = msg match {
        case Msg.Inc => state + 1
        case Msg.Dec => state - 1
      }
      sender ! result
      counter(result)
  }


  sealed trait Msg

  object Msg {
    case object Inc extends Msg
    case object Dec extends Msg
  }
}
