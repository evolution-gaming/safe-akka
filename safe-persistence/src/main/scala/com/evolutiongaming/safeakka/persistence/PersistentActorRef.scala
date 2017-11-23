package com.evolutiongaming.safeakka.persistence

import akka.actor.{ActorSystem, Props}
import com.evolutiongaming.safeakka.actor.{SafeActorRef, Unapply}

object PersistentActorRef {

  def apply[S, SS, C, E](
    setup: SetupPersistentActor[S, SS, C, E],
    name: Option[String] = None)
    (implicit system: ActorSystem, asS: Unapply[S], asC: Unapply[C], asE: Unapply[E]): SafeActorRef[C] = {

    val props = Props(SafePersistentActor(setup))
    val ref = name map { name => system.actorOf(props, name) } getOrElse system.actorOf(props)
    SafeActorRef(ref)
  }
}
