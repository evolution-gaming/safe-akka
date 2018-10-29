package com.evolutiongaming.safeakka.persistence

import akka.actor.{ActorSystem, Props}
import com.evolutiongaming.safeakka.actor.{SafeActorRef, Unapply}

object PersistentActorRef {

  def apply[S: Unapply, SS, C: Unapply, E: Unapply](
    setup: SetupPersistentActor[S, SS, C, E],
    name: Option[String] = None)(implicit
    system: ActorSystem): SafeActorRef[C] = {

    def actor() = SafePersistentActor(setup)

    val props = Props(actor())
    val ref = name map { name => system.actorOf(props, name) } getOrElse system.actorOf(props)
    SafeActorRef(ref)
  }
}
