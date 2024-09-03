package com.evolutiongaming.safeakka.persistence

import akka.persistence.PersistentActor

trait Journaller {

  /**
    * @see [[akka.persistence.Eventsourced.deleteMessages]]
    */
  def delete(toSeqNr: SeqNr): Unit
}

object Journaller {

  def empty: Journaller = Empty

  def apply(actor: PersistentActor): Journaller = (toSeqNr: SeqNr) => actor.deleteMessages(toSeqNr)

  private object Empty extends Journaller {
    def delete(toSeqNr: SeqNr): Unit = {}
  }
}