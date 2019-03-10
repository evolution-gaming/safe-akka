package com.evolutiongaming.safeakka.actor

import org.scalatest.{FunSuite, Matchers}

class UnapplySpec extends FunSuite with Matchers {

  test("either") {
    val unapply = Unapply.either[Int, String]
    unapply.unapply(Right("")) shouldEqual Some(Right(""))
    unapply.unapply("") shouldEqual Some(Right(""))
    unapply.unapply(Left("")) shouldEqual None
    unapply.unapply(Left(0)) shouldEqual Some(Left(0))
    unapply.unapply(0) shouldEqual Some(Left(0))
    unapply.unapply(Right(0)) shouldEqual None
    unapply.unapply(new {}) shouldEqual None
  }

  test("pf") {
    val unapply = Unapply.pf { case s: String => s }
    unapply.unapply("") shouldEqual Some("")
    unapply.unapply(new {}) shouldEqual None
  }
}
