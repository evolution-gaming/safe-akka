package com.evolutiongaming.safeakka.persistence

final case class WithNr[+T](value: T, seqNr: SeqNr) {

  def map[TT](f: T => TT): WithNr[TT] = WithNr(f(value), seqNr)

  def inc: WithNr[T] = WithNr(value, seqNr + 1)
}
