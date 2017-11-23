package com.evolutiongaming.safeakka

package object actor {

  type SetupActor[T] = ActorCtx => (Behavior[T], ActorLog)

  type OnSignal[-T] = Signal[T] => Behavior[T]

  type OnAny[-T] = PartialFunction[Any, Behavior[T]]
}
