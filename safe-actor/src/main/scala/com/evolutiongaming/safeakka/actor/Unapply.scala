package com.evolutiongaming.safeakka.actor

import scala.reflect.ClassTag

@scala.annotation.implicitNotFound(msg = "No Unapply available for ${A}")
trait Unapply[A] {
  def unapply(any: Any): Option[A]
}

object Unapply {

  implicit def apply[A](implicit tag: ClassTag[A]): Unapply[A] = (any: Any) => tag unapply any

  def apply[A](f: Any => Option[A]): Unapply[A] = (any: Any) => f(any)

  def pf[A](pf: PartialFunction[Any, A]): Unapply[A] = (any: Any) => pf.lift(any)

  def of[A](implicit unapply: Unapply[A]): Unapply[A] = unapply

  implicit def either[L, R](implicit l: Unapply[L], r: Unapply[R]): Unapply[Either[L, R]] = pf[Either[L, R]] {
    case Left(l(x))  => Left(x)
    case Right(r(x)) => Right(x)
    case l(x)        => Left(x)
    case r(x)        => Right(x)
  }

  implicit val AnyUnapply: Unapply[Any] = Unapply[Any]
  implicit val AnyUnapply_MistakeProtection: Unapply[Any] = Unapply[Any]

  implicit val AnyRefUnapply: Unapply[AnyRef] = Unapply[AnyRef]
  implicit val AnyRefUnapply_MistakeProtection: Unapply[AnyRef] = Unapply[AnyRef]
}