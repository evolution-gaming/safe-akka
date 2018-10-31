package com.evolutiongaming.safeakka.persistence

import java.io.NotSerializableException

import akka.serialization.Serializer

class TestSerializer extends Serializer {
  import com.evolutiongaming.safeakka.persistence.TestSerializer._

  val identifier = 1234567890

  def toBinary(o: AnyRef) = o match {
    case Msg.Serializable    => Array.empty
    case Msg.NotSerializable => throw new NotSerializableException("")
    case Msg.Illegal         => throw new IllegalArgumentException("")
  }

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]) = {
    Msg.Serializable
  }

  def includeManifest = false
}

object TestSerializer {

  sealed trait Msg
  object Msg {
    object Serializable extends Msg
    object NotSerializable extends Msg
    object Illegal extends Msg
  }
}