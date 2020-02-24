package com.evolutiongaming.safeakka.persistence.async

import com.evolutiongaming.safeakka.actor.ActorCtx
import com.evolutiongaming.safeakka.persistence
import com.evolutiongaming.safeakka.persistence._

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

      def persistenceId = setup.persistenceId

      def log = setup.log

      def journalId = None

      def snapshotId = None

      def onRecoveryStarted(offer: Option[SnapshotOffer[S]], journaller: Journaller, snapshotter: Snapshotter[S]) = {

        val recovering = setup.onRecoveryStarted(offer, journaller, snapshotter)

        new persistence.Recovering[SS, C, E] {
          def state = recovering.state

          def eventHandler(state: SS, event: E, seqNr: SeqNr) = {
            recovering.eventHandler(state, event, seqNr)
          }

          def onCompleted(state: SS, seqNr: SeqNr) = {
            val asyncHandler = recovering.onCompleted(state, seqNr)
            createBehavior(state, seqNr, asyncHandler)
          }

          def onStopped(state: SS, seqNr: SeqNr) = recovering.onStopped(state, seqNr)
        }
      }
      def onStopped(seqNr: SeqNr): Unit = setup.onStopped(seqNr)
    }
  }
}
