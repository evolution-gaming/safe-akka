package com.evolutiongaming.safeakka.persistence

import akka.persistence.PersistentActor

trait Eventsourced {

  /**
    * @see [[akka.persistence.Eventsourced.deleteMessages]]
    */
  def delete(toSeqNr: SeqNr): Unit
}

object Eventsourced {

  def empty: Eventsourced = Empty

  def apply(actor: PersistentActor): Eventsourced = new Eventsourced {
    def delete(toSeqNr: SeqNr): Unit = actor.deleteMessages(toSeqNr)
  }

  private object Empty extends Eventsourced {
    def delete(toSeqNr: SeqNr): Unit = {}
  }
}
