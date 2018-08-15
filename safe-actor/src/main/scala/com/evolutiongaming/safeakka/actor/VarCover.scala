package com.evolutiongaming.safeakka.actor

final case class VarCover[T](private var underCover: T) {
  def map(f: T => T): Unit = underCover = f(underCover)
}