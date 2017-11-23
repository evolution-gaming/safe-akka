package com.evolutiongaming.safeakka.actor

import scala.reflect.ClassTag

@scala.annotation.implicitNotFound(msg = "No Unapply available for ${T}")
trait Unapply[T] {
  def unapply(any: Any): Option[T]
}

object Unapply {

  implicit def apply[T](implicit tag: ClassTag[T]): Unapply[T] = new Unapply[T] {
    def unapply(any: Any) = tag unapply any
  }

  def apply[T](f: Any => Option[T]): Unapply[T] = new Unapply[T] {
    def unapply(any: Any) = f(any)
  }

  def pf[T](pf: PartialFunction[Any, T]): Unapply[T] = new Unapply[T] {
    def unapply(any: Any) = pf.lift(any)
  }

  implicit def either[L, R](implicit l: Unapply[L], r: Unapply[R]): Unapply[Either[L, R]] = pf[Either[L, R]] {
    case Left(l(x))  => Left(x)
    case Right(r(x)) => Right(x)
    case l(x)        => Left(x)
    case r(x)        => Right(x)
  }
}