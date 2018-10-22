package com.evolutiongaming.safeakka.actor

final case class VarCover[A](private var underCover: A) {
  def map(f: A => A): Unit = underCover = f(underCover)
}