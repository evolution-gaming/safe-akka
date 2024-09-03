package com.evolutiongaming.safeakka.persistence.async

import com.evolutiongaming.safeakka.actor.{ActorCtx, ActorLog}
import com.evolutiongaming.safeakka.persistence
import com.evolutiongaming.safeakka.persistence.*

object PersistenceSetupFromAsync {

  def apply[S, SS, C, E](
    setup: AsyncPersistenceSetup[S, SS, C, E],
    ctx: ActorCtx,
  ): PersistenceSetup[S, SS, C, E] = {

    apply(setup, AsyncPersistentBehavior(ctx, setup.log, _, _, _))
  }

  def apply[S, SS, C, E](
    setup: AsyncPersistenceSetup[S, SS, C, E],
    createBehavior: (SS, SeqNr, AsyncSignalHandler[SS, C, E]) => PersistentBehavior[C, E],
  ): PersistenceSetup[S, SS, C, E] = {

    new PersistenceSetup[S, SS, C, E] {

      override def persistenceId: String = setup.persistenceId

      override def log: ActorLog = setup.log

      override def journalId: Option[String] = None

      override def snapshotId: Option[String] = None

      override def onRecoveryStarted(offer: Option[SnapshotOffer[S]], journaller: Journaller, snapshotter: Snapshotter[S]): Recovering = {

        val recovering = setup.onRecoveryStarted(offer, journaller, snapshotter)

        new persistence.Recovering[SS, C, E] {
          override def state: SS = recovering.state

          override def eventHandler(state: SS, event: E, seqNr: SeqNr): SS = {
            recovering.eventHandler(state, event, seqNr)
          }

          override def onCompleted(state: SS, seqNr: SeqNr): PersistentBehavior[C, E] = {
            val asyncHandler = recovering.onCompleted(state, seqNr)
            createBehavior(state, seqNr, asyncHandler)
          }

          override def onStopped(state: SS, seqNr: SeqNr): Unit = recovering.onStopped(state, seqNr)
        }
      }
      override def onStopped(seqNr: SeqNr): Unit = setup.onStopped(seqNr)
    }
  }
}
