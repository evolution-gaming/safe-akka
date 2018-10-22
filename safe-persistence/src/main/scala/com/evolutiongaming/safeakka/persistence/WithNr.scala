package com.evolutiongaming.safeakka.persistence

final case class WithNr[+A](value: A, seqNr: SeqNr) {

  def map[B](ab: A => B): WithNr[B] = WithNr(ab(value), seqNr)

  def inc: WithNr[A] = WithNr(value, seqNr + 1)
}
