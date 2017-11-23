package com.evolutiongaming.safeakka.persistence

import akka.event.LoggingAdapter
import akka.persistence.PersistentActor
import com.evolutiongaming.safeakka.actor.ActorLog

object PersistentActorLogger {
  def apply(actor: PersistentActor, adapter: LoggingAdapter): ActorLog = {
    ActorLog(adapter, actor.persistenceId)
  }
}
