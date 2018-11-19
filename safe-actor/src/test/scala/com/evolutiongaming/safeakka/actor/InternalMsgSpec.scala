package com.evolutiongaming.safeakka.actor

import akka.actor.{ActorRef, Props}
import akka.testkit.TestActorRef
import com.evolutiongaming.safeakka.actor.Behavior.Rcv
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import org.scalatest.WordSpec

class InternalMsgSpec extends WordSpec with ActorSpec {

  "Counter" should {

    "receive private class as msg" in new Scope {
      ref ! Msg.Inc
      expectMsg(1)

      ref ! Msg.InternalInc
      expectMsg(3)
    }
  }

  private trait Scope extends ActorScope {
    val props = Props(SafeActor[Msg](ctx => (setup(ctx, 0), ActorLog.empty)))
    val ref = SafeActorRef[Msg](TestActorRef(props))
  }

  private implicit val dummyMarshaller = Sender.MarshalReply.AnyImpl

  def setup(ctx: ActorCtx, state: Int): Behavior[Msg] = {

    def behavior(state: Int): Behavior[Msg] = {
      case class Internal(sender: Sender)

      val onSignal: OnSignal[Msg] = {
        case Signal.Msg(Msg.Inc, sender)         =>
          val result = state + 1
          sender ! result
          behavior(result)
        case Signal.Msg(Msg.InternalInc, sender) =>
          ctx.self.tell(Internal(sender), ActorRef.noSender)
          behavior(state)
        case _: Signal.System                    =>
          behavior(state)
      }

      val onAny: OnAny[Msg] = {
        case Internal(sender) => (_: Sender) =>
          val result = state + 2
          sender ! result
          behavior(result)
      }

      Rcv(onSignal, onAny)
    }

    behavior(state)
  }


  sealed trait Msg

  object Msg {
    case object Inc extends Msg
    case object InternalInc extends Msg
  }
}
