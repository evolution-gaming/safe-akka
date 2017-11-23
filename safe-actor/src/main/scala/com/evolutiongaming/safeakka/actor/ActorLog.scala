package com.evolutiongaming.safeakka.actor

import akka.actor.ActorSystem
import akka.event.{LogSource, LoggingAdapter}

trait ActorLog {
  def debug(msg: => String): Unit
  def info(msg: => String): Unit
  def warn(msg: => String): Unit
  def error(msg: => String): Unit
  def error(msg: => String, cause: Throwable): Unit
}

object ActorLog {

  def empty: ActorLog = Empty

  def apply(adapter: LoggingAdapter, prefix: String): ActorLog = ActorLog(ActorLog(adapter), prefix)

  def apply(log: ActorLog, prefix: String): ActorLog = new Prefixed(log, prefix)

  def apply(adapter: LoggingAdapter): ActorLog = new Impl(adapter)

  def apply[T: LogSource](system: ActorSystem, source: T): ActorLog = {
    ActorLog(akka.event.Logging(system, source))
  }


  private class Prefixed(log: ActorLog, prefix: String) extends ActorLog {
    def debug(msg: => String): Unit = log.debug(s"$prefix $msg")
    def info(msg: => String): Unit = log.info(s"$prefix $msg")
    def warn(msg: => String): Unit = log.warn(s"$prefix $msg")
    def error(msg: => String): Unit = log.error(s"$prefix $msg")
    def error(msg: => String, cause: Throwable): Unit = log.error(s"$prefix $msg", cause)
  }


  private class Impl(adapter: LoggingAdapter) extends ActorLog {
    def debug(msg: => String): Unit = if (adapter.isDebugEnabled) adapter.debug("{}", msg)
    def info(msg: => String): Unit = if (adapter.isInfoEnabled) adapter.info("{}", msg)
    def warn(msg: => String): Unit = if (adapter.isWarningEnabled) adapter.warning("{}", msg)
    def error(msg: => String): Unit = if (adapter.isErrorEnabled) adapter.error("{}", msg)
    def error(msg: => String, cause: Throwable): Unit = if (adapter.isErrorEnabled) adapter.error(cause, "{}", msg)
  }


  private object Empty extends ActorLog {
    def debug(msg: => String): Unit = ()
    def info(msg: => String): Unit = ()
    def warn(msg: => String): Unit = ()
    def error(msg: => String): Unit = ()
    def error(msg: => String, cause: Throwable): Unit = ()
  }

  implicit class ActorLogOps(val self: ActorLog) extends AnyVal {
    def prefixed(prefix: String): ActorLog = ActorLog(self, prefix)
  }
}