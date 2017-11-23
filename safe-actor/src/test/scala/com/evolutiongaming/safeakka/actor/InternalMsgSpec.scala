package com.evolutiongaming.safeakka.actor

import akka.actor.{ActorRef, Props}
import akka.testkit.TestActorRef
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

  def setup(ctx: ActorCtx, state: Int): Behavior[Msg] = {

    def behavior(state: Int): Behavior[Msg] = {
      case class Internal(sender: ActorRef)

      Behavior.onMsg[Msg] {
        case Signal.Msg(Msg.Inc, sender)         =>
          val result = state + 1
          sender ! result
          behavior(result)
        case Signal.Msg(Msg.InternalInc, sender) =>
          ctx.self.tell(Internal(sender), ActorRef.noSender)
          behavior(state)
      } rcvUnsafe {
        case Internal(sender) =>
          val result = state + 2
          sender ! result
          behavior(result)
      }
    }

    behavior(state)
  }


  sealed trait Msg

  object Msg {
    case object Inc extends Msg
    case object InternalInc extends Msg
  }
}
