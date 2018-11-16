package com.evolutiongaming.safeakka

package object actor {

  type SetupActor[A] = ActorCtx => (Behavior[A], ActorLog)

  type OnSignal[-A] = Signal[A] => Behavior[A]

  type OnAny[-A] = PartialFunction[Any, Sender => Behavior[A]]
}
