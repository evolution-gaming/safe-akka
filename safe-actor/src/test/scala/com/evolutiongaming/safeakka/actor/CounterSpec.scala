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
      expectMsg("1")
      ref ! Msg.Inc
      expectMsg(2)
      ref ! PoisonPill
    }
  }

  private trait Scope extends ActorScope {
    val props = Props(SafeActor[Msg[_]](_ => (counter(0), ActorLog.empty)))
    val ref = SafeActorRef[Msg[_]](TestActorRef(props))
  }

  def counter(state: Int): Behavior[Msg[_]] = Behavior.onMsg[Msg[_]] {
    case Signal.Msg(msg, sender) =>

      val result = msg match {
        case m @ Msg.Inc =>

          implicit val marshaller = m.marshaller
          sender ! state + 1
          state + 1
        case m @ Msg.Dec =>

          implicit val marshaller = m.marshaller
          sender ! (state - 1).toString
          state - 1
      }
      counter(result)
  }


  sealed abstract class Msg[T](implicit val marshaller: Sender.MarshalReply[T])

  object Msg {
    private implicit val intMarshaller = new Sender.MarshalReply[Int] {
      def marshal = identity
    }

    private implicit val stringMarshaller = new Sender.MarshalReply[String] {
      def marshal = identity
    }

    case object Inc extends Msg[Int]
    case object Dec extends Msg[String]
  }
}