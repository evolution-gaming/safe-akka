package com.evolutiongaming.safeakka

import akka.actor.ActorRef

package object actor {

  type SetupActor[A] = ActorCtx => (Behavior[A], ActorLog)

  type OnSignal[-A] = Signal[A] => Behavior[A]

  type Sender = ActorRef

  object Sender {
    lazy val Empty: Sender = ActorRef.noSender
  }

  type OnAny[-A] = PartialFunction[Any, Sender => Behavior[A]]
}
