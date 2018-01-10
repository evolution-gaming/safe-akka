package com.evolutiongaming.safeakka.actor

import akka.actor.{ActorIdentity, Identify, PoisonPill, ReceiveTimeout}
import akka.testkit.TestProbe
import com.evolutiongaming.safeakka.actor.util.ActorSpec
import org.scalatest.{Matchers, WordSpec}

class SafeActorRefSpec extends WordSpec with ActorSpec with Matchers {

  "SafeActorRef" should {

    "proxy messages to ActorRef" in new Scope {
      ref ! "1"
      expectMsg("1")
      lastSender shouldEqual testActor

      ref ! Identify("id")
      expectMsg(ActorIdentity("id", Some(testActor)))

      ref ! ReceiveTimeout
      expectMsg(ReceiveTimeout)

      val probe = TestProbe()
      probe.watch(testActor)
      ref ! PoisonPill
      probe.expectTerminated(testActor)
    }
  }

  private trait Scope extends ActorScope {
    lazy val ref = SafeActorRef[String](testActor)
  }
}
