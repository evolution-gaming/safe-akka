package com.evolutiongaming.util

import scala.util.{Success, Try}

object TryHelper {

  private val TryUnit = Success(())

  implicit class TryOps[A](val self: Try[A]) extends AnyVal {
    def unit: Try[Unit] = self.flatMap(_ => Try.unit)
  }

  implicit class TryObjOps(val self: Try.type) extends AnyVal {
    def unit: Try[Unit] = TryUnit
  }
}
