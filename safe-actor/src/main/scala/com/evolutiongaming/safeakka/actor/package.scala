package com.evolutiongaming.safeakka

import akka.actor.ActorRef

package object actor {

  type SetupActor[T] = ActorCtx => (Behavior[T], ActorLog)

  type OnSignal[-T] = Signal[T] => Behavior[T]

  type Sender = ActorRef

  object Sender {
    lazy val Empty: Sender = ActorRef.noSender
  }

  type OnAny[-T] = PartialFunction[Any, Sender => Behavior[T]]
}
