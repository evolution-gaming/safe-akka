package com.evolutiongaming.safeakka.persistence

import java.io.NotSerializableException

import akka.serialization.SerializerWithStringManifest
import com.evolutiongaming.safeakka.persistence.CounterSpec._

class CounterTestSerializer extends SerializerWithStringManifest {

  private val eventManifest = "E"
  private val snapshotManifest = "S"

  val identifier: Int = 1392023478

  def manifest(x: AnyRef): String = {
    x match {
      case _: Event   => eventManifest
      case _: Counter => snapshotManifest
      case _          =>
        illegalArgument(s"Cannot serialize message of ${ x.getClass } in ${ getClass.getName }")
    }
  }

  def toBinary(a: AnyRef): Array[Byte] = {
    a match {
      case a: Event   =>
        val byte: Byte = a match {
          case Event.Inc => 0
          case Event.Dec => 1
        }
        Array(byte)
      case a: Counter => Array(a.value.toByte, a.seqNr.toByte)
      case _          =>
        illegalArgument(s"Cannot serialize message of ${ a.getClass } in ${ getClass.getName }")
    }
  }

  def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case `eventManifest` =>
        bytes(0) match {
          case 0 => CounterSpec.Event.Inc
          case 1 => CounterSpec.Event.Dec
        }

      case `snapshotManifest` =>
        Counter(value = bytes(0).toInt, seqNr = bytes(0).toLong)
      case _                  =>
        notSerializable(s"Cannot deserialize message for manifest $manifest in ${ getClass.getName }")
    }
  }

  private def notSerializable(msg: String) = throw new NotSerializableException(msg)

  private def illegalArgument(msg: String) = throw new IllegalArgumentException(msg)
}
