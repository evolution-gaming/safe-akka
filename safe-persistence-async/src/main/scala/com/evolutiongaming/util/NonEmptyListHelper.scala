package com.evolutiongaming.util

import cats.data.NonEmptyList

object NonEmptyListHelper {

  implicit class NonEmptyListOps[+A](val self: NonEmptyList[A]) extends AnyVal {

    def foldLeftNel[Z, B](zero: Z)(f: (Z, A) => (Z, B)): (Z, NonEmptyList[B]) = {

      val (z, b) = f(zero, self.head)
      val (zz, bs) = self.tail.foldLeft((z, NonEmptyList.of(b))) {
        case ((z, bs), a) =>
          val (zz, b) = f(z, a)
          (zz, b :: bs)
      }
      (zz, bs.reverse)
    }

    def foreach(f: A => Unit): Unit = {
      f(self.head)
      self.tail.foreach(f)
    }

    def unzip[A1, A2](implicit asPair: A => (A1, A2)): (NonEmptyList[A1], NonEmptyList[A2]) = {
      val (h1, h2) = asPair(self.head)
      val (t1, t2) = self.tail.unzip
      (NonEmptyList(h1, t1), NonEmptyList(h2, t2))
    }

    def unzip3[A1, A2, A3](
      implicit asTriple: A => (A1, A2, A3),
    ): (NonEmptyList[A1], NonEmptyList[A2], NonEmptyList[A3]) = {
      val (h1, h2, h3) = asTriple(self.head)
      val (t1, t2, t3) = self.tail.unzip3
      (NonEmptyList(h1, t1), NonEmptyList(h2, t2), NonEmptyList(h3, t3))
    }

    def size: Int = self.tail.size + 1

    def collect[B](pf: PartialFunction[A, B]): List[B] = self.toList collect pf

    def collectFirst[B](pf: PartialFunction[A, B]): Option[B] = self.toList collectFirst pf
  }

  implicit class NonEmptyListOfNonEmptyListOps[+A](val self: NonEmptyList[NonEmptyList[A]]) extends AnyVal {
    def flatten: NonEmptyList[A] = self.flatMap(identity)
  }
}
