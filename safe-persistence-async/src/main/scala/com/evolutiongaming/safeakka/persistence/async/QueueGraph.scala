package com.evolutiongaming.safeakka.persistence.async

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{RunnableGraph, Sink, Source, SourceQueueWithComplete}
import com.evolutiongaming.nel.Nel

import scala.concurrent.Future

object QueueGraph {

  def apply[T](
    onBatch: Nel[T] => Future[Unit],
    settings: Settings,
  ): RunnableGraph[SourceQueueWithComplete[Future[T]]] = {
    Source
      .queue[Future[T]](settings.bufferSize, OverflowStrategy.backpressure)
      .mapAsync(settings.futureParallelism)(identity)
      .batch[Nel[T]](settings.batchSize, x => Nel(x)) { (xs, x) =>
        x :: xs
      }
      .mapAsync(settings.onBatchParallelism)(xs => onBatch(xs.reverse))
      .to(Sink.ignore)
  }

  /**
    * @param futureParallelism  - number of Future[T] to be processed in parallel
    * @param onBatchParallelism - number of onBatch to be processed in parallel
    * @param batchSize          - max number of elements to batch in case of slow onBatch operation
    */
  final case class Settings(
    bufferSize: Int         = Int.MaxValue,
    futureParallelism: Int  = Runtime.getRuntime.availableProcessors(),
    onBatchParallelism: Int = 5,
    batchSize: Long         = 50,
  )

  object Settings {
    lazy val Default: Settings = Settings()
  }
}
